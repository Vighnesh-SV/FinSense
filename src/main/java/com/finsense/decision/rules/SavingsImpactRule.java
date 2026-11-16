package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.Recommendation;
import com.finsense.decision.RuleResult;
import com.finsense.decision.Severity;

/** How much of the user's free (unreserved) savings would the total price use? */
public final class SavingsImpactRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        double ratio = request.price().ratioTo(snapshot.freeSavings());
        if (ratio <= DecisionThresholds.SAVINGS_SMALL_RATIO) {
            return RuleResult.points(0, Severity.INFO, "The purchase uses little of your free savings.");
        }
        if (ratio <= DecisionThresholds.SAVINGS_MID_RATIO) {
            return RuleResult.points(DecisionThresholds.SAVINGS_MID_PENALTY, Severity.LOW,
                    "The purchase uses part of your free savings.");
        }
        if (ratio <= DecisionThresholds.SAVINGS_LARGE_RATIO) {
            return RuleResult.points(DecisionThresholds.SAVINGS_LARGE_PENALTY, Severity.CAUTION,
                    "The purchase uses a large part of your free savings.");
        }
        return RuleResult.cap(DecisionThresholds.SAVINGS_HIGH_PENALTY, Recommendation.CONSIDER, Severity.HIGH,
                "The purchase costs more than half of your free savings.");
    }
}
