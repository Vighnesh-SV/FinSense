package com.finsense.model;

/**
 * Predicted spending for one category next month.
 * {@code method} names the technique used ("Linear trend" or "Average") so the
 * user - and the examiner - can see how the number was produced.
 */
public record CategoryForecast(Category category, Money lastMonth, Money forecast, String method, int monthsOfData) {

    /** Percentage change from last month to the forecast. */
    public int changePercent() {
        if (lastMonth.isZero()) {
            return forecast.isZero() ? 0 : 100;
        }
        return (int) Math.round((forecast.ratioTo(lastMonth) - 1.0) * 100.0);
    }
}
