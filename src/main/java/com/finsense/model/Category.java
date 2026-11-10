package com.finsense.model;

import com.finsense.model.enums.TransactionType;

/** A built-in or user-owned transaction category. */
public record Category(Long id, Long userId, String name, TransactionType type,
                       String nature, boolean commitment) { }
