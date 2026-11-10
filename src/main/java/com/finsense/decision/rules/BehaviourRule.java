package com.finsense.decision.rules;
import com.finsense.decision.*;
import com.finsense.model.enums.BehaviourType;
import com.finsense.model.enums.Necessity;
public final class BehaviourRule implements DecisionRule {
 public RuleResult evaluate(PurchaseRequest r,FinancialSnapshot s){
  if(s.behaviour()==BehaviourType.IMPULSE_BUYER && r.necessity()==Necessity.WANT)
   return RuleResult.points(DecisionThresholds.IMPULSE_WANT_PENALTY,Severity.HIGH,"For an impulse buyer, pause for "+DecisionThresholds.IMPULSE_WAIT_HOURS+" hours before deciding on a WANT.");
  if(s.behaviour()==BehaviourType.HEAVY_BUYER && r.category()!=null && "DISCRETIONARY".equals(r.category().nature()))
   return RuleResult.points(DecisionThresholds.HEAVY_DISCRETIONARY_PENALTY,Severity.CAUTION,"Frequent discretionary purchases can make this harder to afford.");
  if(s.behaviour()==BehaviourType.SMART_SHOPPER)
   return RuleResult.points(DecisionThresholds.SMART_SHOPPER_BONUS,Severity.INFO,"Your spending profile reflects considered purchase habits.");
  return RuleResult.points(0,Severity.INFO,"No additional behaviour adjustment applies.");
 }
}
