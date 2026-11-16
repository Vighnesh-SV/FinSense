package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.RuleResult;
import com.finsense.decision.Severity;
import com.finsense.model.Money;

import java.time.LocalDate;

/**
 * Is the user spending faster than usual this month?
 *
 * Spending so far is compared with the share of the month's forecast that "should"
 * have been spent by today (forecast x dayOfMonth / daysInMonth). Comparing a partial
 * month with a full-month figure - as before - meant the warning almost never fired.
 */
public final class SpendingTrendRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        LocalDate today = snapshot.asOf();
        if (today.getDayOfMonth() < DecisionThresholds.SPENDING_TREND_MIN_DAY
                || !snapshot.forecastMonthlyExpenses().isPositive()) {
            return RuleResult.neutral("Too early in the month to judge this month's spending pace.");
        }
        double elapsed = (double) today.getDayOfMonth() / today.lengthOfMonth();
        Money expectedSoFar = snapshot.forecastMonthlyExpenses().multiply(java.math.BigDecimal.valueOf(elapsed));
        double pace = snapshot.thisMonthSpending().ratioTo(expectedSoFar);
        if (pace > DecisionThresholds.SPENDING_TREND_RATIO) {
            return RuleResult.points(DecisionThresholds.SPENDING_TREND_PENALTY, Severity.CAUTION,
                    "You are spending about " + Math.round((pace - 1) * 100)
                            + "% faster than usual this month.");
        }
        return RuleResult.neutral("This month's spending pace is normal.");
    }
}
