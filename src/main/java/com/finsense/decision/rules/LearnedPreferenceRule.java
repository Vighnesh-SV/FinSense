package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.RuleResult;
import com.finsense.decision.Severity;
import com.finsense.model.FeedbackStats;

/**
 * The learning rule: FinSense adapts to how the user felt about similar past purchases.
 *
 * After buying something, the user rates it Worth it / Neutral / Regret. For a new request
 * this rule looks at ratings for the same category (if there are enough), otherwise for the
 * same kind of purchase (need or want). The satisfaction score, from -1 (all regretted) to
 * +1 (all worth it), is turned into up to +/-15 points - so the advice changes as the user's
 * own experience grows, instead of staying fixed for everyone.
 */
public final class LearnedPreferenceRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        FeedbackStats stats = similarPurchases(request, snapshot);
        if (stats.total() == 0) {
            return RuleResult.neutral("");                // nothing learned yet
        }
        int points = (int) Math.round(stats.satisfaction() * DecisionThresholds.FEEDBACK_MAX_POINTS);
        String summary = "You rated " + stats.worthIt() + " of " + stats.total() + " past "
                + stats.scope().toLowerCase() + " as worth it and " + stats.regret() + " as a regret";
        if (points < 0) {
            return RuleResult.points(points, Severity.CAUTION, "Learned from you: " + summary + ".");
        }
        return RuleResult.points(points, Severity.INFO, "Learned from you: " + summary + ".");
    }

    private static FeedbackStats similarPurchases(PurchaseRequest request, FinancialSnapshot snapshot) {
        if (request.category() != null && request.category().id() != null) {
            FeedbackStats byCategory = snapshot.feedbackByCategory()
                    .getOrDefault(request.category().id(), FeedbackStats.NONE);
            if (byCategory.total() >= DecisionThresholds.FEEDBACK_MIN_CATEGORY) {
                return byCategory;
            }
        }
        FeedbackStats byNecessity = snapshot.feedbackByNecessity()
                .getOrDefault(request.necessity(), FeedbackStats.NONE);
        return byNecessity.total() >= DecisionThresholds.FEEDBACK_MIN_NECESSITY ? byNecessity : FeedbackStats.NONE;
    }
}
