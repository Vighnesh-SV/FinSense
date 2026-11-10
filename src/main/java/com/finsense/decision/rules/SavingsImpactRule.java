package com.finsense.decision.rules;
import com.finsense.decision.*;
public final class SavingsImpactRule implements DecisionRule {
 public RuleResult evaluate(PurchaseRequest r,FinancialSnapshot s){
  double ratio=r.price().ratioTo(s.freeSavings());
  if(ratio<=DecisionThresholds.SAVINGS_SMALL_RATIO)return RuleResult.points(0,Severity.INFO,"The purchase uses little of your free savings.");
  if(ratio<=DecisionThresholds.SAVINGS_MID_RATIO)return RuleResult.points(DecisionThresholds.SAVINGS_MID_PENALTY,Severity.LOW,"The purchase uses part of your free savings.");
  if(ratio<=DecisionThresholds.SAVINGS_LARGE_RATIO)return RuleResult.points(DecisionThresholds.SAVINGS_LARGE_PENALTY,Severity.CAUTION,"The purchase uses a large part of your free savings.");
  return RuleResult.cap(DecisionThresholds.SAVINGS_HIGH_PENALTY,Recommendation.CONSIDER,Severity.HIGH,"The purchase costs more than half of your free savings.");
 }
}
