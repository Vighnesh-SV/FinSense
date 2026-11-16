package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.RuleResult;
import com.finsense.decision.Severity;
import com.finsense.model.enums.Necessity;

/** Needs earn a small bonus, wants a small penalty. */
public final class NecessityRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        if (request.necessity() == Necessity.NEED) {
            return RuleResult.points(DecisionThresholds.NEED_BONUS, Severity.LOW, "Marked as a need.");
        }
        return RuleResult.points(DecisionThresholds.WANT_PENALTY, Severity.LOW, "Marked as a want.");
    }
}
