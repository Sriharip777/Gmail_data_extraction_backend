package com.gmail.fetcher.exception;


/**
 * Custom exception for Gmail authentication errors
 */
public class GmailAuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GmailAuthException(String message) {
        super(message);
    }

    public GmailAuthException(String message, Throwable cause) {
        super(message, cause);
    }

    public GmailAuthException(Throwable cause) {
        super(cause);
    }
}
