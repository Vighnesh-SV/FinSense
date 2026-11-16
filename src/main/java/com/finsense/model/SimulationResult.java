package com.finsense.model;

import com.finsense.decision.DecisionResult;

import java.util.List;

/** Complete read-only what-if result for the chart, goals and commitment timeline. */
public record SimulationResult(DecisionResult decision, List<MonthlyProjection> projection,
                               List<GoalImpact> goalImpacts, List<CommitmentEnding> commitmentEndings,
                               int instalments) {

    public SimulationResult {
        projection = List.copyOf(projection);
        goalImpacts = List.copyOf(goalImpacts);
        commitmentEndings = List.copyOf(commitmentEndings);
    }
}
