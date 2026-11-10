package com.finsense.model;

import com.finsense.decision.DecisionResult;
/** A stored purchase together with the result of its newest evaluation. */
public record PurchaseEvaluation(long purchaseId,DecisionResult decision) { }
