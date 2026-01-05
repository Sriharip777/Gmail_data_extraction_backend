package com.gmail.fetcher.service;

import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.EmailFilterDTO;
import com.gmail.fetcher.dto.GmailCredentialsDTO;
import com.google.api.services.gmail.Gmail;

import java.util.List;
import java.util.Map;

/**
 * Service interface for Gmail operations
 * UPDATED: Multi-employee support with empId tracking
 *
 * Each employee can connect their own Gmail account, and all operations
 * are isolated by employee ID (empId) rather than email address.
 */
public interface GmailService {

    // ========================================
    // MULTI-EMPLOYEE METHODS (PRIMARY)
    // ========================================

    /**
     * Fetch and save emails for specific employee (with limit)
     *
     * @param empId Employee ID (e.g., "ARGHSE004")
     * @param maxEmails Maximum number of emails to fetch
     * @return Number of emails successfully fetched and saved
     */
    int fetchAndSaveEmailsForAccount(String empId, int maxEmails);

    /**
     * Get statistics for specific employee's Gmail account
     *
     * @param empId Employee ID
     * @return Map containing stats (totalEmails, unreadEmails, starredEmails, etc.)
     */
    Map<String, Object> getStatsForAccount(String empId);

    /**
     * Search emails for specific employee
     *
     * @param empId Employee ID
     * @param filter Filter criteria (subject, from, date range, etc.)
     * @return List of matching emails for this employee
     */
    List<EmailDTO> searchEmailsForAccount(String empId, EmailFilterDTO filter);

    /**
     * Save emails to MongoDB with employee tracking
     *
     * @param emails List of email DTOs to save
     * @param empId Employee ID who owns these emails
     * @param googleEmail Gmail address (for reference)
     */
    void saveEmailsToDatabase(List<EmailDTO> emails, String empId, String googleEmail);

    // ========================================
    // LEGACY METHODS (BACKWARD COMPATIBILITY)
    // ========================================

    /**
     * Fetch all emails from Gmail
     * @deprecated Use fetchAndSaveEmailsForAccount() with empId instead
     */
    List<EmailDTO> fetchAllEmails(GmailCredentialsDTO credentials);

    /**
     * Fetch emails with filter
     * @deprecated Use searchEmailsForAccount() with empId instead
     */
    List<EmailDTO> fetchEmailsWithFilter(GmailCredentialsDTO credentials, EmailFilterDTO filter);

    /**
     * Fetch single email by ID
     */
    EmailDTO fetchEmailById(GmailCredentialsDTO credentials, String messageId);

    /**
     * Search emails in database (across all accounts)
     * @deprecated Use searchEmailsForAccount() for employee-specific search
     */
    List<EmailDTO> searchEmailsInDatabase(EmailFilterDTO filter);

    /**
     * Get Gmail service for credentials file flow
     * Used for initial OAuth setup
     */
    Gmail getGmailServiceWithFile() throws Exception;

    /**
     * Sync emails from Gmail to database
     * @deprecated Use fetchAndSaveEmailsForAccount() with empId instead
     */
    void syncEmailsFromGmail(GmailCredentialsDTO credentials);
}
