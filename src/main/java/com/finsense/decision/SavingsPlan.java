package com.finsense.decision;

import com.finsense.model.Money;

import java.time.LocalDate;

/**
 * Shared savings arithmetic. The decision engine, the goal rule and the what-if
 * simulator all call these methods, so they can never disagree about how fast a
 * goal is reached (they used different formulas before).
 */
public final class SavingsPlan {

    private SavingsPlan() {
    }

    /**
     * Money set aside each month: the declared savings target, or - if the user has
     * no target - whatever disposable income is left over (never negative).
     */
    public static Money monthlyCapacity(Money savingsTarget, Money disposableIncome) {
        if (savingsTarget.isPositive()) {
            return savingsTarget;
        }
        return Money.max(disposableIncome, Money.ZERO);
    }

    /** Whole months needed to save {@code amount}; -1 when nothing is being saved. */
    public static long monthsToSave(Money amount, Money monthlyCapacity) {
        if (!amount.isPositive()) {
            return 0;
        }
        if (!monthlyCapacity.isPositive()) {
            return -1;
        }
        return (long) Math.ceil(amount.ratioTo(monthlyCapacity));
    }

    /** Date a goal is reached, or null when it never is at this saving rate. */
    public static LocalDate eta(LocalDate from, Money remaining, Money monthlyCapacity) {
        long months = monthsToSave(remaining, monthlyCapacity);
        return months < 0 ? null : from.plusMonths(months);
    }
}
