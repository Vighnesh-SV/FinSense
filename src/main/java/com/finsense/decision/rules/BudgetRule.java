package com.finsense.decision.rules;

import com.finsense.decision.DecisionRule;
import com.finsense.decision.DecisionThresholds;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.PurchaseRequest;
import com.finsense.decision.Recommendation;
import com.finsense.decision.RuleResult;
import com.finsense.decision.Severity;
import com.finsense.model.BudgetStatus;
import com.finsense.model.Money;

/**
 * Respects the monthly limit the user set for the purchase's category.
 * Near the limit costs a few points; going over costs more; going far over caps at WAIT.
 */
public final class BudgetRule implements DecisionRule {

    @Override
    public RuleResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        if (request.category() == null || request.category().id() == null) {
            return RuleResult.neutral("");
        }
        BudgetStatus budget = snapshot.budgets().get(request.category().id());
        if (budget == null) {
            return RuleResult.neutral("");                // no budget for this category - nothing to say
        }
        Money after = budget.spentThisMonth().plus(request.monthlyPayment());
        double ratio = after.ratioTo(budget.monthlyLimit());
        String name = budget.category().name();
        String limit = budget.monthlyLimit().format();

        if (ratio > DecisionThresholds.BUDGET_HARD_RATIO) {
            return RuleResult.cap(DecisionThresholds.BUDGET_OVER_PENALTY, Recommendation.WAIT, Severity.HIGH,
                    "This would take " + name + " far past its " + limit + " monthly budget.");
        }
        if (ratio > 1.0) {
            return RuleResult.points(DecisionThresholds.BUDGET_OVER_PENALTY, Severity.CAUTION,
                    "This would put " + name + " over its " + limit + " monthly budget.");
        }
        if (ratio >= DecisionThresholds.BUDGET_WARNING_RATIO) {
            return RuleResult.points(DecisionThresholds.BUDGET_NEAR_PENALTY, Severity.LOW,
                    "This brings " + name + " close to its " + limit + " monthly budget.");
        }
        return RuleResult.points(0, Severity.INFO, name + " stays within its monthly budget.");
    }
}
