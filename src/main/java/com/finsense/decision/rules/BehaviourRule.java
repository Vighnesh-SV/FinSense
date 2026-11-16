package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.RuleResult;
import com.finsense.decision.Severity;
import com.finsense.model.enums.BehaviourType;
import com.finsense.model.enums.Necessity;

/** Adjusts the score for the user's spending personality (from the questionnaire and spending). */
public final class BehaviourRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        BehaviourType type = snapshot.behaviour();
        if (type == BehaviourType.IMPULSE_BUYER && request.necessity() == Necessity.WANT) {
            return RuleResult.points(DecisionThresholds.IMPULSE_WANT_PENALTY, Severity.HIGH,
                    "As an impulse buyer, pause for " + DecisionThresholds.IMPULSE_WAIT_HOURS
                            + " hours before deciding on a want.");
        }
        if (type == BehaviourType.HEAVY_BUYER && request.category() != null && request.category().isDiscretionary()) {
            return RuleResult.points(DecisionThresholds.HEAVY_DISCRETIONARY_PENALTY, Severity.CAUTION,
                    "Frequent discretionary purchases can make this harder to afford.");
        }
        if (type == BehaviourType.SMART_SHOPPER) {
            return RuleResult.points(DecisionThresholds.SMART_SHOPPER_BONUS, Severity.INFO,
                    "Your spending profile reflects considered purchase habits.");
        }
        return RuleResult.neutral("No behaviour adjustment applies.");
    }
}
