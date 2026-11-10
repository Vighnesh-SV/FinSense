package com.finsense.model;

import com.finsense.model.enums.TransactionType;
import java.time.LocalDate;

/** Immutable transaction row; money remains exact through the Money value object. */
public record Transaction(Long id, long userId, Category category, TransactionType type,
                          Money amount, LocalDate date, String description, Long purchaseId) { }
