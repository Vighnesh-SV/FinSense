package com.finsense.model;

/**
 * An expense far above what the user normally spends in that category.
 * {@code typicalAmount} is the median of the category's history and
 * {@code timesTypical} how many times larger this expense is.
 */
public record SpendingAnomaly(Transaction transaction, Money typicalAmount, double timesTypical) { }
