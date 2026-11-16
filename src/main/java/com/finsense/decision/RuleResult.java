package com.finsense.decision;

import java.util.Optional;

/**
 * What one rule concluded: score points (+/-), an optional cap on the verdict
 * (e.g. "no better than WAIT"), how serious it is, and a sentence for the user.
 */
public record RuleResult(int points, Optional<Recommendation> cap, Severity severity, String message) {

    public RuleResult {
        cap = cap == null ? Optional.empty() : cap;
    }

    /** Static factory: a result that only adjusts the score. */
    public static RuleResult points(int points, Severity severity, String message) {
        return new RuleResult(points, Optional.empty(), severity, message);
    }

    /** Static factory: a result that also limits how good the verdict can be. */
    public static RuleResult cap(int points, Recommendation cap, Severity severity, String message) {
        return new RuleResult(points, Optional.of(cap), severity, message);
    }

    /** A rule that has nothing to say (e.g. no budget set for this category). */
    public static RuleResult neutral(String message) {
        return points(0, Severity.INFO, message);
    }
}
