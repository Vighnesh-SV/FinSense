package com.finsense.model;

/** Aggregated income and expense amounts for one calendar month. */
public record MonthlySummary(Money income, Money expenses, Money net) { }
