package com.gmail.fetcher.exception;

/**
 * Exception thrown when email is not found
 */
public class EmailNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor with custom message
     */
    public EmailNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructor with message and cause
     */
    public EmailNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor with cause only
     */
    public EmailNotFoundException(Throwable cause) {
        super(cause);
    }

    /**
     * Static factory method for creating exception by message ID
     */
    public static EmailNotFoundException byMessageId(String messageId) {
        return new EmailNotFoundException("Email not found with ID: " + messageId);
    }

    /**
     * Static factory method for creating exception by email address
     */
    public static EmailNotFoundException byEmail(String email) {
        return new EmailNotFoundException("Email not found for address: " + email);
    }
}
