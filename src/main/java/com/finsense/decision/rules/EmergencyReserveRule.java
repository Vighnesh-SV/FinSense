package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.Recommendation;
import com.finsense.decision.RuleResult;
import com.finsense.decision.Severity;
import com.finsense.model.Money;
import com.finsense.model.enums.Necessity;

/**
 * Keeps three months of expenses untouched. A purchase that would dig into that
 * reserve is limited to WAIT - except a small NEED (medicine, a bus pass...), which
 * only loses points, because blocking essentials would be bad advice.
 */
public final class EmergencyReserveRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        Money afterPayment = snapshot.freeSavings().minus(request.monthlyPayment());
        if (!afterPayment.isLessThan(snapshot.emergencyReserve())) {
            return RuleResult.neutral("The emergency reserve stays covered after this purchase.");
        }
        if (isSmallNeed(request, snapshot)) {
            return RuleResult.points(DecisionThresholds.SMALL_NEED_RESERVE_PENALTY, Severity.CAUTION,
                    "Your emergency reserve is short, but this is a small essential purchase.");
        }
        return RuleResult.cap(0, Recommendation.WAIT, Severity.CRITICAL,
                "This purchase would leave free savings below the emergency reserve.");
    }

    private static boolean isSmallNeed(PurchaseRequest request, FinancialSnapshot snapshot) {
        return request.necessity() == Necessity.NEED
                && request.monthlyPayment().ratioTo(snapshot.income()) <= DecisionThresholds.SMALL_NEED_INCOME_RATIO;
    }
}
