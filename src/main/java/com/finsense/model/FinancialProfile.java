package com.finsense.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A user's financial profile storing baseline income, expense baselines, and savings.
 *
 * OOP concept: Immutability. All fields are private and final; there are no
 * setters. Once constructed, a FinancialProfile object cannot be mutated,
 * eliminating side effects and making instances safe to share across threads.
 */
public class FinancialProfile {

    private final long userId;
    private final Money monthlyIncome;
    private final Money fixedExpenses;
    private final Money variableExpenses;
    private final Money currentSavings;
    private final Money monthlySavingsTarget;
    private final LocalDateTime updatedAt;

    public FinancialProfile(long userId,
                            Money monthlyIncome,
                            Money fixedExpenses,
                            Money variableExpenses,
                            Money currentSavings,
                            Money monthlySavingsTarget,
                            LocalDateTime updatedAt) {
        this.userId = userId;
        this.monthlyIncome = Objects.requireNonNull(monthlyIncome, "monthlyIncome");
        this.fixedExpenses = Objects.requireNonNull(fixedExpenses, "fixedExpenses");
        this.variableExpenses = Objects.requireNonNull(variableExpenses, "variableExpenses");
        this.currentSavings = Objects.requireNonNull(currentSavings, "currentSavings");
        this.monthlySavingsTarget = Objects.requireNonNull(monthlySavingsTarget, "monthlySavingsTarget");
        this.updatedAt = updatedAt;
    }

    public long getUserId() {
        return userId;
    }

    public Money getMonthlyIncome() {
        return monthlyIncome;
    }

    public Money getFixedExpenses() {
        return fixedExpenses;
    }

    public Money getVariableExpenses() {
        return variableExpenses;
    }

    public Money getCurrentSavings() {
        return currentSavings;
    }

    public Money getMonthlySavingsTarget() {
        return monthlySavingsTarget;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Total recurring monthly expenses excluding commitments: fixed + variable.
     */
    public Money totalExpenses() {
        return fixedExpenses.plus(variableExpenses);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinancialProfile that)) return false;
        return userId == that.userId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(userId);
    }

    @Override
    public String toString() {
        return "FinancialProfile[user=" + userId + ", income=" + monthlyIncome + "]";
    }
}

