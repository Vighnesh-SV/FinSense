package com.finsense.model.enums;

/**
 * How important a savings goal is.
 *
 * name() is what is stored in MySQL; toString() returns a readable label, so
 * ComboBoxes, tables and messages show "Smart shopper" rather than SMART_SHOPPER.
 */
public enum GoalPriority {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    private final String label;

    GoalPriority(String label) {
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
