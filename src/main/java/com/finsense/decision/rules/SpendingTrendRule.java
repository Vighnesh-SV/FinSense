package com.finsense.decision.rules;
import com.finsense.decision.*;
public final class SpendingTrendRule implements DecisionRule {
 public RuleResult evaluate(PurchaseRequest r,FinancialSnapshot s){
  double ratio=s.thisMonthSpending().ratioTo(s.averageMonthlyExpenses());
  if(ratio>DecisionThresholds.SPENDING_TREND_RATIO)return RuleResult.points(DecisionThresholds.SPENDING_TREND_PENALTY,Severity.CAUTION,"This month's spending is more than "+DecisionThresholds.SPENDING_TREND_ABOVE_PERCENT+"% above the recent monthly average.");
  return RuleResult.points(0,Severity.INFO,"This month's spending is near or below the recent average.");
 }
}
