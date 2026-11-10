package com.finsense.decision.rules;
import com.finsense.decision.*;
import com.finsense.model.Money;
import com.finsense.model.enums.GoalPriority;
public final class GoalImpactRule implements DecisionRule {
 public RuleResult evaluate(PurchaseRequest r,FinancialSnapshot s){
  if(s.activeGoals().isEmpty())return RuleResult.points(0,Severity.INFO,"No active goals need to be adjusted.");
  Money monthlyCapacity=Money.max(s.savingsTarget(),Money.max(s.disposableIncome(),Money.ZERO));
  long delay=monthlyCapacity.isPositive()?(long)Math.ceil(r.price().ratioTo(monthlyCapacity)):0;
  int points=(int)Math.max(DecisionThresholds.GOAL_PENALTY_CAP,delay*DecisionThresholds.GOAL_DELAY_PENALTY_PER_MONTH*s.activeGoals().size());
  boolean misses=s.activeGoals().stream().anyMatch(g->{
   if(g.priority()!=GoalPriority.HIGH)return false;
   if(!monthlyCapacity.isPositive())return false;
   long before=(long)Math.ceil(g.remainingAmount().ratioTo(monthlyCapacity));
   long after=(long)Math.ceil(g.remainingAmount().plus(r.price()).ratioTo(monthlyCapacity));
   java.time.LocalDate baselineEta=s.asOf().plusMonths(before),purchaseEta=s.asOf().plusMonths(after);
   return !baselineEta.isAfter(g.deadline()) && purchaseEta.isAfter(g.deadline());
  });
  String message="The purchase may delay active savings goals by about "+delay+" month(s).";
  if(misses)return RuleResult.cap(points,Recommendation.CONSIDER,Severity.HIGH,"A HIGH-priority goal is projected to miss its deadline after this purchase.");
  return RuleResult.points(points,delay>0?Severity.CAUTION:Severity.INFO,message);
 }
}
