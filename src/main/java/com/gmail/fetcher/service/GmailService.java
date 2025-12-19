package com.gmail.fetcher.service;


import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.EmailFilterDTO;
import com.gmail.fetcher.dto.GmailCredentialsDTO;
import com.google.api.services.gmail.Gmail;

import java.util.List;

/**
 * Service interface for Gmail operations
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
    void saveEmailsToDatabase(List<EmailDTO> emails);

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
}

