package com.finsense.exception;

/**
 * Thrown when user input is invalid (empty name, negative amount, weak password ...).
 * The message is written for the end user and is shown directly in an error dialog.
 */
public class ValidationException extends FinSenseException {

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public ValidationException(String message) {
        this(null, message);
    }

    /** Name of the input field that failed validation, or null if not field-specific. */
    public String getField() {
        return field;
    }
}
