package com.finsense.decision.rules;
import com.finsense.decision.*;
public final class EmergencyReserveRule implements DecisionRule {
 public RuleResult evaluate(PurchaseRequest r,FinancialSnapshot s){
  if(s.freeSavings().minus(r.monthlyPayment()).isLessThan(s.emergencyReserve()))
   return RuleResult.cap(0,Recommendation.WAIT,Severity.CRITICAL,"This purchase would leave free savings below the emergency reserve.");
  return RuleResult.points(0,Severity.INFO,"The emergency reserve remains covered after this purchase.");
 }
}
