package com.gmail.fetcher.exception;


/**
 * Exception thrown when database operation fails
 */
public class DatabaseOperationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatabaseOperationException(String message) {
        super(message);
    }

    public DatabaseOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

