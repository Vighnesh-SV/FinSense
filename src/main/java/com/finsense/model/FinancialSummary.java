package com.finsense.model;

/**
 * Computed financial summary snapshot of a user's monthly budget.
 *
 * Java record (Java 16+): An immutable data carrier. All fields are final and
 * accessors (income(), totalExpenses(), etc.) are generated automatically.
 *
 * Note: disposableIncome is calculated on the fly and is NEVER stored in the database.
 * It represents free cash flow after meeting essential expenses, commitments,
 * and savings targets, and can be negative if obligations exceed income.
 */
public record FinancialSummary(
        Money income,
        Money totalExpenses,
        Money activeCommitments,
        Money savingsTarget,
        Money disposableIncome,
        double commitmentRatio,
        Money emergencyReserve
) {
}

