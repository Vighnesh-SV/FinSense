package com.finsense.decision;

import com.finsense.decision.rules.*;
import java.util.List;

/** Builds the production rule strategy list in documented evaluation order. */
public final class DecisionEngineFactory {
    private DecisionEngineFactory() { }
    public static DecisionEngine create() {
        return new DecisionEngine(List.of(new AffordabilityRule(),new SavingsImpactRule(),new EmergencyReserveRule(),
                new GoalImpactRule(),new CommitmentLoadRule(),new SpendingTrendRule(),new NecessityRule(),new BehaviourRule()));
    }
}
