package com.finsense.model.enums;

/**
 * Type of recurring monthly financial obligation.
 *
 * Matches the SQL ENUM ('EMI', 'LOAN', 'RENT', 'INSURANCE', 'OTHER')
 * in the commitments table. Each constant carries a user-friendly label
 * so UI controls like ComboBox directly display readable names via toString().
 */
public enum CommitmentType {
    EMI("EMI"),
    LOAN("Loan"),
    RENT("Rent"),
    INSURANCE("Insurance"),
    OTHER("Other");

    private final String label;

    CommitmentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}

