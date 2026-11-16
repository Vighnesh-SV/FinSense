package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.Recommendation;
import com.finsense.decision.RuleResult;
import com.finsense.decision.Severity;

/** Are EMIs, rent and other fixed commitments already eating most of the income? */
public final class CommitmentLoadRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        double ratio = snapshot.activeCommitments().ratioTo(snapshot.income());
        if (ratio > DecisionThresholds.COMMITMENT_WAIT_RATIO) {
            return RuleResult.cap(DecisionThresholds.COMMITMENT_PENALTY, Recommendation.WAIT, Severity.CRITICAL,
                    "Commitments exceed " + DecisionThresholds.COMMITMENT_WAIT_PERCENT + "% of income.");
        }
        if (ratio > DecisionThresholds.COMMITMENT_CAUTION_RATIO) {
            return RuleResult.points(DecisionThresholds.COMMITMENT_PENALTY, Severity.CAUTION,
                    "Commitments use more than " + DecisionThresholds.COMMITMENT_CAUTION_PERCENT + "% of income.");
        }
        return RuleResult.neutral("Commitments are within a manageable share of income.");
    }
}
