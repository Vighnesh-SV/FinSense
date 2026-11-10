package com.finsense.decision;

import com.finsense.exception.IncompleteProfileException;
import com.finsense.exception.ValidationException;
import com.finsense.model.Money;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure engine: applies injected rules, score bands and restrictive caps without database calls. */
public final class DecisionEngine {
    private final List<DecisionRule> rules;
    public DecisionEngine(List<DecisionRule> rules) { this.rules=List.copyOf(rules); }
    public DecisionResult evaluate(PurchaseRequest request,FinancialSnapshot snapshot) {
        if(request==null || request.price()==null || request.price().getPaise()<DecisionThresholds.NO_PROFILE_PRICE_MIN)
            throw new ValidationException("Price must be greater than ₹0.");
        if(snapshot==null) throw new IncompleteProfileException("Please complete your financial profile first.");
        List<RuleResult> results=rules.stream().map(rule->rule.evaluate(request,snapshot)).toList();
        int score=DecisionThresholds.START_SCORE+results.stream().mapToInt(RuleResult::points).sum();
        score=Math.max(DecisionThresholds.SCORE_MIN,Math.min(DecisionThresholds.SCORE_MAX,score));
        Recommendation band=band(score),finalRecommendation=band;
        for(RuleResult result:results) if(result.cap().isPresent() && result.cap().get().ordinal()>finalRecommendation.ordinal())
            finalRecommendation=result.cap().get();
        Money affordable= snapshot.freeSavings().plus(Money.max(snapshot.disposableIncome(),Money.ZERO));
        boolean hardStop=request.price().isGreaterThan(affordable);
        if(hardStop) finalRecommendation=Recommendation.AVOID;
        List<String> explanation=new ArrayList<>();
        explanation.add("FinSense suggests "+finalRecommendation+" for "+request.productName()+" (score "+score+"/100).");
        results.stream().filter(r->!r.message().isBlank()).sorted(Comparator.comparing(RuleResult::severity).reversed())
                .map(RuleResult::message).forEach(explanation::add);
        if(finalRecommendation!=band && !hardStop) {
            Recommendation appliedLimit=finalRecommendation;
            String reason=results.stream().filter(r->r.cap().filter(c->c==appliedLimit).isPresent())
                    .map(RuleResult::message).findFirst().orElse("a safety rule");
            explanation.add("Score suggested "+band+", limited to "+finalRecommendation+" because "+reason);
        }
        if(hardStop) explanation.add("Hard stop: the price exceeds free savings plus non-negative disposable income.");
        List<String> warnings=new ArrayList<>();
        if(snapshot.fallbackDataUsed()) warnings.add("Spending history is limited; declared monthly expenses were used.");
        if(snapshot.activeGoals().isEmpty()) warnings.add("No active savings goals are set.");
        explanation.add("This is general guidance, not financial advice.");
        return new DecisionResult(finalRecommendation,score,explanation,warnings,snapshot,LocalDateTime.now());
    }
    private static Recommendation band(int score) {
        if(score>=DecisionThresholds.BUY_MIN_SCORE)return Recommendation.BUY;
        if(score>=DecisionThresholds.CONSIDER_MIN_SCORE)return Recommendation.CONSIDER;
        if(score>=DecisionThresholds.WAIT_MIN_SCORE)return Recommendation.WAIT;
        return Recommendation.AVOID;
    }
}
