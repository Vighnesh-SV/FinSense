package com.finsense.decision;

import com.finsense.model.Money;
import com.finsense.model.enums.BehaviourType;
import java.time.LocalDate;
import java.util.List;

/** Frozen finances used by one deterministic decision; no DAO or mutable entity enters the engine. */
public record FinancialSnapshot(Money income,Money monthlyExpenses,Money activeCommitments,Money savingsTarget,
        Money disposableIncome,Money emergencyReserve,Money freeSavings,List<GoalSnapshot> activeGoals,
        BehaviourType behaviour,Money thisMonthSpending,Money averageMonthlyExpenses,
        boolean fallbackDataUsed,LocalDate asOf) {
    public FinancialSnapshot { activeGoals=List.copyOf(activeGoals); }
}
