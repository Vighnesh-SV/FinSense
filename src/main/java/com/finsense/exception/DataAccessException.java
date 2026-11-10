package com.finsense.exception;

/**
 * Thrown when a database operation fails.
 *
 * JDBC reports problems with the checked java.sql.SQLException. DAO classes
 * catch it and re-throw it wrapped in this exception, so that the layers above
 * the DAO never depend on JDBC types (the original error is kept as the cause).
 */
public class DataAccessException extends FinSenseException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
