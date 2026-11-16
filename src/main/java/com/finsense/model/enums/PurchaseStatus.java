package com.finsense.model.enums;

/**
 * Life-cycle of a purchase idea.
 *
 * name() is what is stored in MySQL; toString() returns a readable label, so
 * ComboBoxes, tables and messages show "Smart shopper" rather than SMART_SHOPPER.
 */
public enum PurchaseStatus {
    EVALUATED("Evaluated"),
    PURCHASED("Purchased"),
    DROPPED("Dropped");

    private final String label;

    PurchaseStatus(String label) {
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
