package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.Recommendation;
import com.finsense.decision.RuleResult;
import com.finsense.decision.Severity;
import com.finsense.model.enums.Necessity;

/**
 * How big is the monthly payment compared with the money left over each month?
 * Uses the monthly payment, so an instalment plan is judged per instalment.
 */
public final class AffordabilityRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        if (!snapshot.disposableIncome().isPositive()) {
            String message = "Disposable income is zero or negative after commitments and the savings target.";
            if (request.necessity() == Necessity.WANT) {
                return RuleResult.cap(DecisionThresholds.UNAFFORDABLE_PENALTY, Recommendation.WAIT, Severity.HIGH,
                        "A want is limited to WAIT because disposable income is zero or negative.");
            }
            return RuleResult.points(DecisionThresholds.UNAFFORDABLE_PENALTY, Severity.HIGH, message);
        }

        double ratio = request.monthlyPayment().ratioTo(snapshot.disposableIncome());
        if (ratio <= DecisionThresholds.AFFORDABLE_RATIO) {
            return RuleResult.points(DecisionThresholds.AFFORDABLE_PENALTY, Severity.INFO,
                    "The payment is a small share of your monthly disposable income.");
        }
        if (ratio <= DecisionThresholds.MODERATE_RATIO) {
            return RuleResult.points(DecisionThresholds.MODERATE_PENALTY, Severity.LOW,
                    "The payment uses a noticeable share of your monthly disposable income.");
        }
        if (ratio <= DecisionThresholds.FULL_DISPOSABLE_RATIO) {
            return RuleResult.points(DecisionThresholds.FULL_PENALTY, Severity.CAUTION,
                    "The payment uses most of your monthly disposable income.");
        }
        if (ratio <= DecisionThresholds.HIGH_DISPOSABLE_RATIO) {
            return RuleResult.points(DecisionThresholds.HIGH_PENALTY, Severity.HIGH,
                    "The payment is several times your monthly disposable income.");
        }
        return RuleResult.points(DecisionThresholds.UNAFFORDABLE_PENALTY, Severity.HIGH,
                "The payment is far above your monthly disposable income.");
    }
}
