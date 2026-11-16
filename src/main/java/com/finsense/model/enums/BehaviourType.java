package com.finsense.model.enums;

/**
 * Spending personality assigned by the behaviour classifier.
 *
 * name() is what is stored in MySQL; toString() returns a readable label, so
 * ComboBoxes, tables and messages show "Smart shopper" rather than SMART_SHOPPER.
 */
public enum BehaviourType {
    IMPULSE_BUYER("Impulse buyer"),
    HEAVY_BUYER("Heavy buyer"),
    SMART_SHOPPER("Smart shopper");

    private final String label;

    BehaviourType(String label) {
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
