package com.finsense.service;

import com.finsense.decision.DecisionEvaluator;
import com.finsense.decision.DecisionResult;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.exception.IncompleteProfileException;
import com.finsense.exception.ValidationException;
import com.finsense.model.Commitment;
import com.finsense.model.CommitmentEnding;
import com.finsense.model.FinancialProfile;
import com.finsense.model.GoalImpact;
import com.finsense.model.Money;
import com.finsense.model.MonthlyProjection;
import com.finsense.model.SavingsGoal;
import com.finsense.model.SimulationResult;
import com.finsense.model.enums.GoalStatus;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.TransactionType;
import com.finsense.model.Category;
import com.finsense.util.ValidationUtil;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Read-only what-if service. It calls the evaluator but never invokes a write DAO. */
public class SimulationService {
    public static final int PROJECTION_MONTHS=12;
    private final DecisionEvaluator evaluator;private final FinancialService financialService;private final SavingsGoalService goalService;
    public SimulationService(){this(new DecisionEvaluator(),new FinancialService(),new SavingsGoalService());}
    public SimulationService(DecisionEvaluator evaluator,FinancialService financialService,SavingsGoalService goalService){
        this.evaluator=evaluator;this.financialService=financialService;this.goalService=goalService;
    }
    public SimulationResult simulate(PurchaseRequest request,int instalments){
        if(instalments<1 || instalments>PROJECTION_MONTHS)throw new ValidationException("Instalments must be between 1 and 12.");
        FinancialSnapshot snapshot=evaluator.buildSnapshot();
        // The verdict must reflect the payment plan being simulated, not always an upfront payment.
        PurchaseRequest planned=new PurchaseRequest(request.productName(),request.category(),request.price(),
                request.necessity(),request.description(),instalments);
        DecisionResult decision=evaluator.evaluate(planned,snapshot);
        FinancialProfile profile=financialService.getProfile().orElseThrow(()->new IncompleteProfileException("Please complete your financial profile first."));
        List<Commitment> commitments=financialService.getCommitments();
        List<SavingsGoal> goals=goalService.goals().stream().filter(g->g.status()==GoalStatus.ACTIVE).toList();
        List<MonthlyProjection> projection=project(profile.getCurrentSavings(),profile.getMonthlySavingsTarget(),snapshot.disposableIncome(),
                request.price(),commitments,snapshot.asOf(),instalments,PROJECTION_MONTHS);
        List<GoalImpact> impacts=goalImpacts(goals,profile.getMonthlySavingsTarget(),request.price(),snapshot.asOf());
        List<CommitmentEnding> endings=commitmentEndings(commitments,snapshot.asOf(),PROJECTION_MONTHS);
        return new SimulationResult(decision,projection,impacts,endings,instalments);
    }
    public SimulationResult simulate(String name,Category category,String price,Necessity necessity,int instalments){
        String cleanName=ValidationUtil.requireText(name,"Product name",100);
        if(category==null || category.type()!=TransactionType.EXPENSE)throw new ValidationException("Choose an expense category.");
        return simulate(new PurchaseRequest(cleanName,category,ValidationUtil.parsePositiveAmount(price,"Price"),
                ValidationUtil.requireSelection(necessity,"Necessity"),null),instalments);
    }
    /** Pure projection math makes month-end commitment expiry easy to verify. */
    public static List<MonthlyProjection> project(Money currentSavings,Money monthlyTarget,Money disposable,Money price,
            List<Commitment> commitments,LocalDate asOf,int instalments,int months){
        List<MonthlyProjection> result=new ArrayList<>();Money baseline=currentSavings,withPurchase=currentSavings;
        YearMonth start=YearMonth.from(asOf).minusMonths(1);
        Money unpaidPurchase=price;
        for(int index=1;index<=months;index++){
            YearMonth month=start.plusMonths(index);Money released=commitments.stream()
                    .filter(c->c.getEndDate()!=null && c.getEndDate().isBefore(month.atEndOfMonth()))
                    .filter(c->c.isActiveOn(asOf))
                    .map(Commitment::getMonthlyAmount).reduce(Money.ZERO,Money::plus);
            Money monthlyGrowth=monthlyTarget.plus(released);
            baseline=baseline.plus(monthlyGrowth);
            Money payment=Money.ZERO;
            if(index<=instalments){payment=index==instalments?unpaidPurchase:price.divide(instalments);unpaidPurchase=unpaidPurchase.minus(payment);}
            withPurchase=withPurchase.plus(monthlyGrowth).minus(payment);
            Money baselineDisposable=disposable.plus(released);
            result.add(new MonthlyProjection(index,month,baseline,withPurchase,baselineDisposable,baselineDisposable.minus(payment)));
        }
        return List.copyOf(result);
    }
    public static List<GoalImpact> goalImpacts(List<SavingsGoal> goals,Money monthlyTarget,Money price,LocalDate asOf){
        List<GoalImpact> impacts=new ArrayList<>();
        for(SavingsGoal goal:goals){
            long before=monthsToReach(goal.remainingAmount(),monthlyTarget),after=monthsToReach(goal.remainingAmount().plus(price),monthlyTarget);
            LocalDate baseline=before<0?null:asOf.plusMonths(before),with=after<0?null:asOf.plusMonths(after);
            long delay=before<0||after<0?0:Math.max(0,after-before);
            impacts.add(new GoalImpact(goal.name(),goal.priority(),goal.deadline(),baseline,with,delay,with!=null&&with.isAfter(goal.deadline())));
        }
        return List.copyOf(impacts);
    }
    private static long monthsToReach(Money amount,Money monthly){return monthly.isPositive()?(long)Math.ceil(amount.ratioTo(monthly)):-1;}
    public static List<CommitmentEnding> commitmentEndings(List<Commitment> commitments,LocalDate asOf,int months){
        YearMonth start=YearMonth.from(asOf);return commitments.stream().filter(c->c.getEndDate()!=null)
                .filter(c->!c.getEndDate().isBefore(asOf)&&!c.getEndDate().isAfter(start.plusMonths(months-1L).atEndOfMonth()))
                .map(c->new CommitmentEnding(c.getName(),c.getEndDate(),(int)ChronoUnit.MONTHS.between(start,c.getEndDate().withDayOfMonth(1))+1,c.getMonthlyAmount()))
                .toList();
    }
}
