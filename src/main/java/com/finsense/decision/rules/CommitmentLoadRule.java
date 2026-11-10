package com.finsense.decision.rules;
import com.finsense.decision.*;
public final class CommitmentLoadRule implements DecisionRule {
 public RuleResult evaluate(PurchaseRequest r,FinancialSnapshot s){
  double ratio=s.activeCommitments().ratioTo(s.income());
  if(ratio>DecisionThresholds.COMMITMENT_WAIT_RATIO)return RuleResult.cap(DecisionThresholds.COMMITMENT_PENALTY,Recommendation.WAIT,Severity.CRITICAL,"Commitments exceed "+DecisionThresholds.COMMITMENT_WAIT_PERCENT+"% of income.");
  if(ratio>DecisionThresholds.COMMITMENT_CAUTION_RATIO)return RuleResult.points(DecisionThresholds.COMMITMENT_PENALTY,Severity.CAUTION,"Commitments use more than "+DecisionThresholds.COMMITMENT_CAUTION_PERCENT+"% of income.");
  return RuleResult.points(0,Severity.INFO,"Commitments are within a manageable share of income.");
 }
}
