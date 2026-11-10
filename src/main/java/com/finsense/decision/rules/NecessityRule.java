package com.finsense.decision.rules;
import com.finsense.decision.*;
import com.finsense.model.enums.Necessity;
public final class NecessityRule implements DecisionRule {
 public RuleResult evaluate(PurchaseRequest r,FinancialSnapshot s){
  return r.necessity()==Necessity.NEED?RuleResult.points(DecisionThresholds.NEED_BONUS,Severity.LOW,"Marked as a NEED."):
    RuleResult.points(DecisionThresholds.WANT_PENALTY,Severity.LOW,"Marked as a WANT.");
 }
}
