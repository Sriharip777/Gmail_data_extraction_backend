package com.gmail.fetcher.service;

import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.EmailFilterDTO;
import com.gmail.fetcher.dto.GmailCredentialsDTO;
import com.google.api.services.gmail.Gmail;

import java.util.List;
import java.util.Map;

/**
 * Service interface for Gmail operations
 * UPDATED: Added multi-account support methods
 */
public interface GmailService {

    /**
     * Fetch all emails from Gmail
     */
    List<EmailDTO> fetchAllEmails(GmailCredentialsDTO credentials);

    /**
     * Fetch emails with filter
     */
    List<EmailDTO> fetchEmailsWithFilter(GmailCredentialsDTO credentials, EmailFilterDTO filter);

    /**
     * Fetch single email by ID
     */
    EmailDTO fetchEmailById(GmailCredentialsDTO credentials, String messageId);

    /**
     * Save emails to MongoDB
     */
    void saveEmailsToDatabase(List<EmailDTO> emails, String ownerEmail);

    /**
     * Search emails in database
     */
    List<EmailDTO> searchEmailsInDatabase(EmailFilterDTO filter);

    /**
     * Get Gmail service for credentials file flow
     */
    Gmail getGmailServiceWithFile() throws Exception;

    /**
     * Sync emails from Gmail to database
     */
    void syncEmailsFromGmail(GmailCredentialsDTO credentials);

    // NEW METHODS for multi-account support

    /**
     * Fetch and save emails for specific Gmail account (with limit)
     */
    int fetchAndSaveEmailsForAccount(String ownerEmail, int maxEmails);

    /**
     * Get statistics for specific account
     */
    Map<String, Object> getStatsForAccount(String ownerEmail);

    /**
     * Search emails for specific account
     */
    List<EmailDTO> searchEmailsForAccount(String ownerEmail, EmailFilterDTO filter);
}
