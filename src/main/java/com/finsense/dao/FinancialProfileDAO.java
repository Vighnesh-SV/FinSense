package com.finsense.dao;

import com.finsense.model.FinancialProfile;

import java.util.Optional;

/**
 * Data Access Object for the {@code financial_profiles} table.
 *
 * OOP concept: Inheritance. Extends BaseDAO to reuse JDBC queryOne, update,
 * getMoney, and getDateTime methods without boilerplate JDBC code.
 *
 * Each user has at most one financial profile (PK is user_id).
 */
public class FinancialProfileDAO extends BaseDAO {

    private static final RowMapper<FinancialProfile> PROFILE_MAPPER = rs -> new FinancialProfile(
            rs.getLong("user_id"),
            getMoney(rs, "monthly_income"),
            getMoney(rs, "fixed_expenses"),
            getMoney(rs, "variable_expenses"),
            getMoney(rs, "current_savings"),
            getMoney(rs, "monthly_savings_target"),
            getDateTime(rs, "updated_at")
    );

    /**
     * Finds the financial profile for the specified user.
     * User isolation: scoped strictly by userId.
     */
    public Optional<FinancialProfile> findByUserId(long userId) {
        return queryOne(
                "SELECT user_id, monthly_income, fixed_expenses, variable_expenses, " +
                        "current_savings, monthly_savings_target, updated_at " +
                        "FROM financial_profiles WHERE user_id = ?",
                PROFILE_MAPPER,
                userId
        );
    }

    /**
     * Inserts or updates the user's financial profile.
     * Uses MySQL's INSERT ... ON DUPLICATE KEY UPDATE so that saving works
     * identically whether the profile is brand new or already exists.
     */
    public void upsert(FinancialProfile p) {
        update(
                "INSERT INTO financial_profiles " +
                        "(user_id, monthly_income, fixed_expenses, variable_expenses, current_savings, monthly_savings_target) " +
                        "VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "monthly_income = VALUES(monthly_income), " +
                        "fixed_expenses = VALUES(fixed_expenses), " +
                        "variable_expenses = VALUES(variable_expenses), " +
                        "current_savings = VALUES(current_savings), " +
                        "monthly_savings_target = VALUES(monthly_savings_target)",
                p.getUserId(),
                p.getMonthlyIncome(),
                p.getFixedExpenses(),
                p.getVariableExpenses(),
                p.getCurrentSavings(),
                p.getMonthlySavingsTarget()
        );
    }
}

