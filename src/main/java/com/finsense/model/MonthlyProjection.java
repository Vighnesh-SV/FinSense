package com.finsense.model;

import java.time.YearMonth;
/** One monthly point for baseline and hypothetical savings balances. */
public record MonthlyProjection(int monthNumber,YearMonth month,Money baselineSavings,Money withPurchaseSavings,
                                Money baselineDisposable,Money withPurchaseDisposable) { }
