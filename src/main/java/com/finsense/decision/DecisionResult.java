package com.finsense.decision;

import java.time.LocalDateTime;
import java.util.List;

/** Complete explainable result and immutable snapshot saved in evaluation history. */
public record DecisionResult(Recommendation recommendation,int score,List<String> explanationLines,
                             List<String> warnings,FinancialSnapshot snapshot,LocalDateTime evaluatedAt) {
    public DecisionResult { explanationLines=List.copyOf(explanationLines);warnings=List.copyOf(warnings); }
}
