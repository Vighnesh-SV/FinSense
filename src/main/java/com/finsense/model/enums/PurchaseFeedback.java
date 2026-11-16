package com.finsense.model.enums;

/**
 * How the user felt about a purchase afterwards; the learning signal for personalised advice.
 *
 * name() is what is stored in MySQL; toString() returns a readable label, so
 * ComboBoxes, tables and messages show "Smart shopper" rather than SMART_SHOPPER.
 */
public enum PurchaseFeedback {
    WORTH_IT("Worth it"),
    NEUTRAL("Neutral"),
    REGRET("Regret");

    private final String label;

    PurchaseFeedback(String label) {
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
