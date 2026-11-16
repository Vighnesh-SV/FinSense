package com.finsense.model.enums;

/**
 * Whether the user considers a purchase essential.
 *
 * name() is what is stored in MySQL; toString() returns a readable label, so
 * ComboBoxes, tables and messages show "Smart shopper" rather than SMART_SHOPPER.
 */
public enum Necessity {
    NEED("Need"),
    WANT("Want");

    private final String label;

    Necessity(String label) {
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
