package com.finsense.decision;

/** Strategy contract that lets DecisionEngine apply rules polymorphically without database access. */
public interface DecisionRule {
    RuleResult evaluate(PurchaseRequest request,FinancialSnapshot snapshot);
}
