package com.finsense.model;

import com.finsense.decision.Recommendation;
/** Purchase row plus the latest verdict for the purchase history list. */
public record PurchaseSummary(Purchase purchase,Recommendation latestRecommendation,Integer latestScore) { }
