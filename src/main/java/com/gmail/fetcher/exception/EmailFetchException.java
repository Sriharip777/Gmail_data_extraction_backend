package com.gmail.fetcher.exception;

/**
 * Custom exception for email fetching errors
 */
public class EmailFetchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailFetchException(String message) {
        super(message);
    }

    public EmailFetchException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmailFetchException(Throwable cause) {
        super(cause);
    }
}

