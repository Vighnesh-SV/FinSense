package com.finsense.model;

import java.time.LocalDate;

/** Thirty-day signals used to blend transaction behaviour with questionnaire scores. */
public record SpendingSignals(LocalDate firstTransaction,double discretionarySpendShare,
                              double discretionaryTransactionShare,int regrettedPurchases,int purchasedCount) {
    public boolean hasThirtyDays(LocalDate today) {
        return firstTransaction!=null && !firstTransaction.isAfter(today.minusDays(30));
    }
    public double regretShare() { return purchasedCount==0?0.0:(double)regrettedPurchases/purchasedCount; }
}
