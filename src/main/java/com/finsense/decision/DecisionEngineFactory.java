package com.finsense.decision;

import com.finsense.decision.rules.AffordabilityRule;
import com.finsense.decision.rules.BehaviourRule;
import com.finsense.decision.rules.BudgetRule;
import com.finsense.decision.rules.CommitmentLoadRule;
import com.finsense.decision.rules.EmergencyReserveRule;
import com.finsense.decision.rules.GoalImpactRule;
import com.finsense.decision.rules.LearnedPreferenceRule;
import com.finsense.decision.rules.NecessityRule;
import com.finsense.decision.rules.SavingsImpactRule;
import com.finsense.decision.rules.SpendingTrendRule;

import java.util.List;

/**
 * Factory: builds the production engine with its rules in a fixed, documented order.
 * Adding a new rule means writing one DecisionRule class and listing it here (Open/Closed principle).
 */
public final class DecisionEngineFactory {

    private DecisionEngineFactory() {
    }

    public static DecisionEngine create() {
        return new DecisionEngine(List.of(
                new AffordabilityRule(),
                new SavingsImpactRule(),
                new EmergencyReserveRule(),
                new GoalImpactRule(),
                new CommitmentLoadRule(),
                new SpendingTrendRule(),
                new BudgetRule(),
                new NecessityRule(),
                new BehaviourRule(),
                new LearnedPreferenceRule()));
    }
}
