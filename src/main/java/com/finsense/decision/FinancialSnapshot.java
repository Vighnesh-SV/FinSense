package com.finsense.decision;

import com.finsense.model.BudgetStatus;
import com.finsense.model.FeedbackStats;
import com.finsense.model.Money;
import com.finsense.model.enums.BehaviourType;
import com.finsense.model.enums.Necessity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The user's finances frozen at one moment - the only input the decision rules see.
 * It is a record (immutable), and the compact constructor makes defensive copies of
 * the collections, so no rule can change the data another rule reads.
 *
 * @param forecastMonthlyExpenses next month's predicted non-commitment spending (ForecastService)
 * @param budgets                 category id -> this month's budget status
 * @param feedbackByCategory      category id -> how the user rated past purchases in it
 * @param feedbackByNecessity     NEED / WANT -> how the user rated past purchases of that kind
 */
public record FinancialSnapshot(Money income, Money monthlyExpenses, Money activeCommitments, Money savingsTarget,
                                Money disposableIncome, Money emergencyReserve, Money freeSavings,
                                List<GoalSnapshot> activeGoals, BehaviourType behaviour,
                                Money thisMonthSpending, Money averageMonthlyExpenses,
                                boolean fallbackDataUsed, LocalDate asOf,
                                Money forecastMonthlyExpenses,
                                Map<Long, BudgetStatus> budgets,
                                Map<Long, FeedbackStats> feedbackByCategory,
                                Map<Necessity, FeedbackStats> feedbackByNecessity) {

    public FinancialSnapshot {
        activeGoals = List.copyOf(activeGoals);
        budgets = Map.copyOf(budgets);
        feedbackByCategory = Map.copyOf(feedbackByCategory);
        feedbackByNecessity = Map.copyOf(feedbackByNecessity);
    }

    /** Snapshot without forecast, budgets or feedback (the forecast falls back to the average). */
    public FinancialSnapshot(Money income, Money monthlyExpenses, Money activeCommitments, Money savingsTarget,
                             Money disposableIncome, Money emergencyReserve, Money freeSavings,
                             List<GoalSnapshot> activeGoals, BehaviourType behaviour,
                             Money thisMonthSpending, Money averageMonthlyExpenses,
                             boolean fallbackDataUsed, LocalDate asOf) {
        this(income, monthlyExpenses, activeCommitments, savingsTarget, disposableIncome, emergencyReserve,
                freeSavings, activeGoals, behaviour, thisMonthSpending, averageMonthlyExpenses, fallbackDataUsed,
                asOf, averageMonthlyExpenses, Map.of(), Map.of(), Map.of());
    }

    /** What the user can put aside each month - shared by the engine and the simulator. */
    public Money monthlySavingCapacity() {
        return SavingsPlan.monthlyCapacity(savingsTarget, disposableIncome);
    }
}
