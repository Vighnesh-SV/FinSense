package com.finsense.decision;

import com.finsense.dao.FeedbackDAO;
import com.finsense.exception.IncompleteProfileException;
import com.finsense.model.FinancialProfile;
import com.finsense.model.FinancialSummary;
import com.finsense.model.Money;
import com.finsense.model.enums.BehaviourType;
import com.finsense.model.enums.GoalStatus;
import com.finsense.model.BehaviourProfile;
import com.finsense.service.BehaviourService;
import com.finsense.service.BudgetService;
import com.finsense.service.FinancialService;
import com.finsense.service.ForecastService;
import com.finsense.service.SavingsGoalService;
import com.finsense.service.TransactionService;
import com.finsense.util.SessionManager;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Bridge between the live application and the pure engine (Adapter).
 * It reads everything the rules need from the services, freezes it into one
 * FinancialSnapshot, and hands that to the DecisionEngine - so the rules never touch the database.
 */
public final class DecisionEvaluator {

    private final FinancialService financialService;
    private final TransactionService transactionService;
    private final SavingsGoalService goalService;
    private final BehaviourService behaviourService;
    private final DecisionEngine engine;
    private final ForecastService forecastService;
    private final BudgetService budgetService;
    private final FeedbackDAO feedbackDAO;

    public DecisionEvaluator() {
        this(new FinancialService(), new TransactionService(), new SavingsGoalService(),
                new BehaviourService(), DecisionEngineFactory.create());
    }

    /** Constructor injection: tests can pass their own services or engine. */
    public DecisionEvaluator(FinancialService financialService, TransactionService transactionService,
                             SavingsGoalService goalService, BehaviourService behaviourService,
                             DecisionEngine engine) {
        this.financialService = financialService;
        this.transactionService = transactionService;
        this.goalService = goalService;
        this.behaviourService = behaviourService;
        this.engine = engine;
        this.forecastService = new ForecastService();
        this.budgetService = new BudgetService();
        this.feedbackDAO = new FeedbackDAO();
    }

    public DecisionResult evaluate(PurchaseRequest request) {
        return engine.evaluate(request, buildSnapshot());
    }

    public DecisionResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        return engine.evaluate(request, snapshot);
    }

    public FinancialSnapshot buildSnapshot() {
        LocalDate today = LocalDate.now();
        FinancialProfile profile = financialService.getProfile()
                .filter(p -> p.getMonthlyIncome().isPositive())
                .orElseThrow(() -> new IncompleteProfileException("Please complete your financial profile first."));
        FinancialSummary declared = financialService.calculateSummary(today);
        Money commitments = declared.activeCommitments();

        // Real spending history wins over the declared figures once a full month exists.
        boolean fallback = !transactionService.hasAtLeastOneFullMonthOfData();
        Money expenses = fallback
                ? profile.totalExpenses()
                : transactionService.averageMonthlyExpenses(DecisionThresholds.SPENDING_AVERAGE_MONTHS);
        Money forecast = fallback ? expenses : forecastService.totalForecast();

        Money disposable = profile.getMonthlyIncome()
                .minus(expenses)
                .minus(commitments)
                .minus(profile.getMonthlySavingsTarget());
        Money reserve = expenses.plus(commitments).times(DecisionThresholds.EMERGENCY_RESERVE_MONTHS);

        List<GoalSnapshot> goals = goalService.goals().stream()
                .filter(g -> g.status() == GoalStatus.ACTIVE)
                .map(g -> new GoalSnapshot(g.remainingAmount(), g.deadline(), g.priority()))
                .toList();
        BehaviourType behaviour = behaviourService.currentProfile().map(BehaviourProfile::type).orElse(null);
        long userId = SessionManager.getInstance().requireUserId();

        return new FinancialSnapshot(
                profile.getMonthlyIncome(), expenses, commitments, profile.getMonthlySavingsTarget(),
                disposable, reserve, goalService.freeSavings(), goals, behaviour,
                transactionService.thisMonthExpenses(), expenses, fallback, today,
                forecast,
                budgetService.statusesByCategory(YearMonth.from(today)),
                feedbackDAO.statsByCategory(userId),
                feedbackDAO.statsByNecessity(userId));
    }
}
