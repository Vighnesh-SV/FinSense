package com.finsense.decision;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * The complete, explainable outcome of one evaluation.
 *
 * @param affordableFrom for WAIT/AVOID verdicts, the first month the purchase fits without
 *                       touching the emergency reserve (null if not within two years, or not needed)
 * @param reasons        each rule's message with its severity, most serious first (for the UI)
 */
public record DecisionResult(Recommendation recommendation, int score, List<String> explanationLines,
                             List<String> warnings, FinancialSnapshot snapshot, LocalDateTime evaluatedAt,
                             YearMonth affordableFrom, List<Reason> reasons) {

    public DecisionResult {
        explanationLines = List.copyOf(explanationLines);
        warnings = List.copyOf(warnings);
        reasons = List.copyOf(reasons);
    }

    public DecisionResult(Recommendation recommendation, int score, List<String> explanationLines,
                          List<String> warnings, FinancialSnapshot snapshot, LocalDateTime evaluatedAt) {
        this(recommendation, score, explanationLines, warnings, snapshot, evaluatedAt, null, List.of());
    }
}
