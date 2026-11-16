package com.finsense.model;

import com.finsense.decision.Recommendation;
import com.finsense.model.enums.PurchaseFeedback;

/**
 * A purchase plus its latest verdict, for the history list.
 * latestRecommendation/latestScore are null before the first evaluation;
 * feedback is null until the user rates a bought item.
 */
public record PurchaseSummary(Purchase purchase, Recommendation latestRecommendation, Integer latestScore,
                              PurchaseFeedback feedback) { }
