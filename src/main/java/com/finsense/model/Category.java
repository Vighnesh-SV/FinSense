package com.finsense.model;

import com.finsense.model.enums.TransactionType;

/** A built-in (userId == null) or user-owned transaction category. */
public record Category(Long id, Long userId, String name, TransactionType type,
                       String nature, boolean commitment) {

    /** ComboBoxes call toString(): show "Groceries", not the whole record. */
    @Override
    public String toString() {
        return name;
    }

    public boolean isDiscretionary() {
        return "DISCRETIONARY".equals(nature);
    }
}
