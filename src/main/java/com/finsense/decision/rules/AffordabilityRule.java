package com.finsense.decision.rules;
import com.finsense.decision.*;
import com.finsense.model.enums.Necessity;
public final class AffordabilityRule implements DecisionRule {
 public RuleResult evaluate(PurchaseRequest r,FinancialSnapshot s){
  if(s.disposableIncome().isZero()||s.disposableIncome().isNegative()) {
   var cap=r.necessity()==Necessity.WANT?Recommendation.WAIT:null;
   return cap==null?RuleResult.points(DecisionThresholds.UNAFFORDABLE_PENALTY,Severity.HIGH,"Disposable income is zero or negative after commitments and savings target."):
    RuleResult.cap(DecisionThresholds.UNAFFORDABLE_PENALTY,cap,Severity.HIGH,"A WANT is capped at WAIT because disposable income is zero or negative.");
  }
  double ratio=r.price().ratioTo(s.disposableIncome());
  if(ratio<=DecisionThresholds.AFFORDABLE_RATIO)return RuleResult.points(DecisionThresholds.AFFORDABLE_PENALTY,Severity.INFO,"The price is a small share of disposable income.");
  if(ratio<=DecisionThresholds.MODERATE_RATIO)return RuleResult.points(DecisionThresholds.MODERATE_PENALTY,Severity.LOW,"The price uses a noticeable share of disposable income.");
  if(ratio<=DecisionThresholds.FULL_DISPOSABLE_RATIO)return RuleResult.points(DecisionThresholds.FULL_PENALTY,Severity.CAUTION,"The price uses a large share of disposable income.");
  if(ratio<=DecisionThresholds.HIGH_DISPOSABLE_RATIO)return RuleResult.points(DecisionThresholds.HIGH_PENALTY,Severity.HIGH,"The price is several times your monthly disposable income.");
  return RuleResult.points(DecisionThresholds.UNAFFORDABLE_PENALTY,Severity.HIGH,"The price is far above your monthly disposable income.");
 }
}
