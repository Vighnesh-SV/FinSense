package com.finsense.model;

import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.PurchaseStatus;
import java.time.LocalDateTime;

/** Persisted purchase question whose evaluations are kept separately as history rows. */
public record Purchase(Long id,long userId,String productName,Category category,Money price,String description,
                       Necessity necessity,PurchaseStatus status,LocalDateTime createdAt) { }
