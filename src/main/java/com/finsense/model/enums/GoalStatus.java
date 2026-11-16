package com.finsense.model.enums;

/**
 * Life-cycle of a savings goal.
 *
 * name() is what is stored in MySQL; toString() returns a readable label, so
 * ComboBoxes, tables and messages show "Smart shopper" rather than SMART_SHOPPER.
 */
public enum GoalStatus {
    ACTIVE("Active"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String label;

    GoalStatus(String label) {
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
