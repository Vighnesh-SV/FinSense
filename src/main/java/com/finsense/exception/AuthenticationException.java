package com.finsense.exception;

/**
 * Thrown when login fails or when an action needs a logged-in user but nobody is logged in.
 */
public class AuthenticationException extends FinSenseException {

    public AuthenticationException(String message) {
        super(message);
    }
}
