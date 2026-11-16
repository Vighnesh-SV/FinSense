package com.finsense.model.enums;

/**
 * Where a behaviour classification came from.
 *
 * name() is what is stored in MySQL; toString() returns a readable label, so
 * ComboBoxes, tables and messages show "Smart shopper" rather than SMART_SHOPPER.
 */
public enum BehaviourSource {
    QUESTIONNAIRE("Questionnaire"),
    SPENDING("Spending history");

    private final String label;

    BehaviourSource(String label) {
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
