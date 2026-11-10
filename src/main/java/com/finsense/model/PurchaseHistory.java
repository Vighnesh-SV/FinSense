package com.finsense.model;

import com.finsense.decision.Recommendation;
import java.time.LocalDateTime;

/** Frozen verdict and figures from one purchase evaluation. */
public record PurchaseHistory(Long id,long purchaseId,Recommendation recommendation,int score,String explanation,String warnings,
        Money snapshotIncome,Money snapshotExpenses,Money snapshotDisposable,Money snapshotFreeSavings,
        String snapshotBehaviour,LocalDateTime evaluatedAt) { }
