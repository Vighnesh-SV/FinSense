package com.finsense.exception;

/**
 * Base class of every application-specific exception in FinSense.
 *
 * It extends RuntimeException (an *unchecked* exception) so that service and
 * DAO methods do not have to declare "throws" everywhere, while the UI layer
 * can still catch one common type: catch (FinSenseException e) { ... }.
 * Subclasses (inheritance) describe what kind of problem occurred.
 */
public class FinSenseException extends RuntimeException {

    public FinSenseException(String message) {
        super(message);
    }

    public FinSenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
