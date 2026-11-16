package com.finsense.service;

import com.finsense.decision.DecisionEvaluator;
import com.finsense.decision.DecisionResult;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.SavingsPlan;
import com.finsense.exception.ValidationException;
import com.finsense.model.Category;
import com.finsense.model.Commitment;
import com.finsense.model.CommitmentEnding;
import com.finsense.model.GoalImpact;
import com.finsense.model.Money;
import com.finsense.model.MonthlyProjection;
import com.finsense.model.SavingsGoal;
import com.finsense.model.SimulationResult;
import com.finsense.model.enums.GoalStatus;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.ValidationUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The read-only "what-if" simulator. It evaluates a purchase and projects savings for
 * twelve months, but never calls a write method - nothing is saved.
 *
 * It uses the same snapshot and the same SavingsPlan arithmetic as the Purchase Advisor,
 * so both screens always agree on free savings and goal delays.
 */
public class SimulationService {

    public static final int PROJECTION_MONTHS = 12;

    private final DecisionEvaluator evaluator;
    private final FinancialService financialService;
    private final SavingsGoalService goalService;

    public SimulationService() {
        this(new DecisionEvaluator(), new FinancialService(), new SavingsGoalService());
    }

    public SimulationService(DecisionEvaluator evaluator, FinancialService financialService,
                             SavingsGoalService goalService) {
        this.evaluator = evaluator;
        this.financialService = financialService;
        this.goalService = goalService;
    }

    public SimulationResult simulate(String name, Category category, String price, Necessity necessity,
                                     int instalments) {
        String cleanName = ValidationUtil.requireText(name, "Product name", 100);
        if (category == null || category.type() != TransactionType.EXPENSE) {
            throw new ValidationException("Choose an expense category.");
        }
        PurchaseRequest request = new PurchaseRequest(cleanName, category,
                ValidationUtil.parsePositiveAmount(price, "Price"),
                ValidationUtil.requireSelection(necessity, "Need or want"), null);
        return simulate(request, instalments);
    }

    public SimulationResult simulate(PurchaseRequest request, int instalments) {
        if (instalments < 1 || instalments > PROJECTION_MONTHS) {
            throw new ValidationException("Instalments must be between 1 and 12.");
        }
        FinancialSnapshot snapshot = evaluator.buildSnapshot();
        // The verdict must reflect the payment plan being simulated, not always an upfront payment.
        PurchaseRequest planned = new PurchaseRequest(request.productName(), request.category(), request.price(),
                request.necessity(), request.description(), instalments);
        DecisionResult decision = evaluator.evaluate(planned, snapshot);

        List<Commitment> commitments = financialService.getCommitments();
        List<SavingsGoal> goals = goalService.goals().stream()
                .filter(g -> g.status() == GoalStatus.ACTIVE)
                .toList();
        Money capacity = snapshot.monthlySavingCapacity();

        // Start from FREE savings (what the advisor uses), not total savings that include goal money.
        List<MonthlyProjection> projection = project(snapshot.freeSavings(), capacity, snapshot.disposableIncome(),
                request.price(), commitments, snapshot.asOf(), instalments, PROJECTION_MONTHS);
        List<GoalImpact> impacts = goalImpacts(goals, capacity, request.price(), snapshot.asOf());
        List<CommitmentEnding> endings = commitmentEndings(commitments, snapshot.asOf(), PROJECTION_MONTHS);
        return new SimulationResult(decision, projection, impacts, endings, instalments);
    }

    /**
     * Month-by-month savings with and without the purchase. When a commitment ends, its
     * monthly amount is added to savings from the following month on.
     */
    public static List<MonthlyProjection> project(Money startingSavings, Money monthlySaving, Money disposable,
                                                  Money price, List<Commitment> commitments, LocalDate asOf,
                                                  int instalments, int months) {
        List<MonthlyProjection> result = new ArrayList<>();
        Money baseline = startingSavings;
        Money withPurchase = startingSavings;
        Money unpaid = price;
        YearMonth first = YearMonth.from(asOf);

        for (int index = 1; index <= months; index++) {
            YearMonth month = first.plusMonths(index - 1L);
            Money released = commitments.stream()
                    .filter(c -> c.getEndDate() != null && c.getEndDate().isBefore(month.atEndOfMonth()))
                    .filter(c -> c.isActiveOn(asOf))
                    .map(Commitment::getMonthlyAmount)
                    .reduce(Money.ZERO, Money::plus);
            Money growth = monthlySaving.plus(released);
            baseline = baseline.plus(growth);

            Money payment = Money.ZERO;
            if (index <= instalments) {
                payment = index == instalments ? unpaid : price.divide(instalments);   // last one takes the rounding
                unpaid = unpaid.minus(payment);
            }
            withPurchase = withPurchase.plus(growth).minus(payment);

            Money baselineDisposable = disposable.plus(released);
            result.add(new MonthlyProjection(index, month, baseline, withPurchase,
                    baselineDisposable, baselineDisposable.minus(payment)));
        }
        return List.copyOf(result);
    }

    /** When each goal is reached with and without the purchase (shared SavingsPlan maths). */
    public static List<GoalImpact> goalImpacts(List<SavingsGoal> goals, Money monthlySaving, Money price,
                                               LocalDate asOf) {
        List<GoalImpact> impacts = new ArrayList<>();
        for (SavingsGoal goal : goals) {
            LocalDate baseline = SavingsPlan.eta(asOf, goal.remainingAmount(), monthlySaving);
            LocalDate with = SavingsPlan.eta(asOf, goal.remainingAmount().plus(price), monthlySaving);
            long delay = baseline == null || with == null ? 0 : Math.max(0, ChronoUnit.MONTHS.between(baseline, with));
            boolean misses = with != null && with.isAfter(goal.deadline());
            impacts.add(new GoalImpact(goal.name(), goal.priority(), goal.deadline(), baseline, with, delay, misses));
        }
        return List.copyOf(impacts);
    }

    public static List<CommitmentEnding> commitmentEndings(List<Commitment> commitments, LocalDate asOf, int months) {
        YearMonth start = YearMonth.from(asOf);
        LocalDate horizon = start.plusMonths(months - 1L).atEndOfMonth();
        return commitments.stream()
                .filter(c -> c.getEndDate() != null)
                .filter(c -> !c.getEndDate().isBefore(asOf) && !c.getEndDate().isAfter(horizon))
                .map(c -> new CommitmentEnding(c.getName(), c.getEndDate(),
                        (int) ChronoUnit.MONTHS.between(start, YearMonth.from(c.getEndDate())) + 1,
                        c.getMonthlyAmount()))
                .toList();
    }
}
