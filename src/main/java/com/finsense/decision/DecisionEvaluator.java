package com.finsense.decision;

import com.finsense.exception.IncompleteProfileException;
import com.finsense.model.FinancialProfile;
import com.finsense.model.FinancialSummary;
import com.finsense.model.Money;
import com.finsense.model.enums.GoalStatus;
import com.finsense.service.BehaviourService;
import com.finsense.service.FinancialService;
import com.finsense.service.SavingsGoalService;
import com.finsense.service.TransactionService;
import java.time.LocalDate;
import java.util.List;

/** Adapts live services to the engine's immutable input, keeping data access outside decision rules. */
public final class DecisionEvaluator {
    private final FinancialService financialService;
    private final TransactionService transactionService;
    private final SavingsGoalService goalService;
    private final BehaviourService behaviourService;
    private final DecisionEngine engine;
    public DecisionEvaluator() { this(new FinancialService(),new TransactionService(),new SavingsGoalService(),new BehaviourService(),DecisionEngineFactory.create()); }
    public DecisionEvaluator(FinancialService financialService,TransactionService transactionService,
                             SavingsGoalService goalService,BehaviourService behaviourService,DecisionEngine engine) {
        this.financialService=financialService;this.transactionService=transactionService;this.goalService=goalService;
        this.behaviourService=behaviourService;this.engine=engine;
    }
    public DecisionResult evaluate(PurchaseRequest request) { return engine.evaluate(request,buildSnapshot()); }
    public DecisionResult evaluate(PurchaseRequest request,FinancialSnapshot snapshot) { return engine.evaluate(request,snapshot); }
    public FinancialSnapshot buildSnapshot() {
        LocalDate today=LocalDate.now();
        FinancialProfile profile=financialService.getProfile().filter(p->p.getMonthlyIncome().isPositive())
                .orElseThrow(()->new IncompleteProfileException("Please complete your financial profile first."));
        FinancialSummary declared=financialService.calculateSummary(today);
        boolean fallback=!transactionService.hasAtLeastOneFullMonthOfData();
        Money expenses=fallback?profile.totalExpenses():transactionService.averageMonthlyExpenses(DecisionThresholds.SPENDING_AVERAGE_MONTHS);
        Money disposable=profile.getMonthlyIncome().minus(expenses).minus(declared.activeCommitments()).minus(profile.getMonthlySavingsTarget());
        Money reserve=expenses.plus(declared.activeCommitments()).times(DecisionThresholds.EMERGENCY_RESERVE_MONTHS);
        List<GoalSnapshot> goals=goalService.goals().stream().filter(g->g.status()==GoalStatus.ACTIVE)
                .map(g->new GoalSnapshot(g.remainingAmount(),g.deadline(),g.priority())).toList();
        var behaviour=behaviourService.currentProfile().orElse(null);
        Money average= fallback?expenses:transactionService.averageMonthlyExpenses(DecisionThresholds.SPENDING_AVERAGE_MONTHS);
        return new FinancialSnapshot(profile.getMonthlyIncome(),expenses,declared.activeCommitments(),profile.getMonthlySavingsTarget(),
                disposable,reserve,goalService.freeSavings(),goals,behaviour==null?null:behaviour.type(),
                transactionService.thisMonthExpenses(),average,fallback,today);
    }
}
