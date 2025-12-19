package com.gmail.fetcher.exception;


/**
 * Exception thrown when email filter is invalid
 */
public class InvalidFilterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidFilterException(String message) {
        super(message);
    }

    public InvalidFilterException(String message, Throwable cause) {
        super(message, cause);
    }
}

