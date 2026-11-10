package com.finsense.decision;

import java.util.Optional;

/** Points, optional restrictiveness cap, severity and explanation from one rule. */
public record RuleResult(int points,Optional<Recommendation> cap,Severity severity,String message) {
    public RuleResult { cap=cap==null?Optional.empty():cap; }
    public static RuleResult points(int points,Severity severity,String message) { return new RuleResult(points,Optional.empty(),severity,message); }
    public static RuleResult cap(int points,Recommendation cap,Severity severity,String message) { return new RuleResult(points,Optional.of(cap),severity,message); }
}
