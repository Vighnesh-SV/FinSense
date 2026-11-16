package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.GoalSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.Recommendation;
import com.finsense.decision.RuleResult;
import com.finsense.decision.SavingsPlan;
import com.finsense.decision.Severity;
import com.finsense.model.Money;
import com.finsense.model.enums.GoalPriority;

import java.time.LocalDate;

/**
 * How many months would this purchase push back the user's savings goals?
 * The money is spent once, so the delay is counted once - not once per goal.
 * A HIGH-priority goal that would miss its deadline limits the verdict to CONSIDER.
 */
public final class GoalImpactRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        if (snapshot.activeGoals().isEmpty()) {
            return RuleResult.neutral("No active goals need to be adjusted.");
        }
        Money capacity = snapshot.monthlySavingCapacity();
        long delay = Math.max(0, SavingsPlan.monthsToSave(request.price(), capacity));
        int points = (int) Math.max(DecisionThresholds.GOAL_PENALTY_CAP,
                delay * DecisionThresholds.GOAL_DELAY_PENALTY_PER_MONTH);

        boolean highGoalMissed = snapshot.activeGoals().stream()
                .anyMatch(goal -> missesDeadline(goal, request.price(), capacity, snapshot.asOf()));
        if (highGoalMissed) {
            return RuleResult.cap(points, Recommendation.CONSIDER, Severity.HIGH,
                    "A high-priority goal is projected to miss its deadline after this purchase.");
        }
        return RuleResult.points(points, delay > 0 ? Severity.CAUTION : Severity.INFO,
                "The purchase may delay your savings goals by about " + delay + " month(s).");
    }

    /** True when a HIGH goal is on track today but would be late after the purchase. */
    private static boolean missesDeadline(GoalSnapshot goal, Money price, Money capacity, LocalDate asOf) {
        if (goal.priority() != GoalPriority.HIGH || !capacity.isPositive()) {
            return false;
        }
        LocalDate before = SavingsPlan.eta(asOf, goal.remainingAmount(), capacity);
        LocalDate after = SavingsPlan.eta(asOf, goal.remainingAmount().plus(price), capacity);
        return !before.isAfter(goal.deadline()) && after.isAfter(goal.deadline());
    }
}
