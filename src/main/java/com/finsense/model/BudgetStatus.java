package com.finsense.model;

/** A category's monthly budget limit and how much has been spent against it this month. */
public record BudgetStatus(long budgetId, Category category, Money monthlyLimit, Money spentThisMonth) {

    public Money remaining() {
        return Money.max(Money.ZERO, monthlyLimit.minus(spentThisMonth));
    }

    /** Share of the limit used, e.g. 0.8 = 80 %. */
    public double usedRatio() {
        return spentThisMonth.ratioTo(monthlyLimit);
    }

    public boolean isOver() {
        return spentThisMonth.isGreaterThan(monthlyLimit);
    }
}
