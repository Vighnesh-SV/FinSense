package com.finsense.exception;

/**
 * Thrown when a feature (e.g. the Purchase Decision Engine) needs financial
 * information that the user has not entered yet. The UI reacts by sending the
 * user to the My Profile screen.
 */
public class IncompleteProfileException extends FinSenseException {

    public IncompleteProfileException(String message) {
        super(message);
    }
}
