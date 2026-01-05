package com.gmail.fetcher.repository;

import com.gmail.fetcher.entity.EmailMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for EmailMessage entity
 * MongoDB queries using Spring Data conventions
 * UPDATED: Added empId-based methods for multi-employee support
 */
@Repository
public interface EmailMessageRepository extends MongoRepository<EmailMessage, String> {

    // ========================================
    // MULTI-EMPLOYEE METHODS (PRIMARY - USE THESE)
    // ========================================

    /**
     * Find all emails for specific employee
     */
    List<EmailMessage> findByOwnerEmpId(String ownerEmpId);

    /**
     * Count total emails for employee
     */
    long countByOwnerEmpId(String ownerEmpId);

    /**
     * Find email by employee and message ID (for duplicate checking)
     */
    Optional<EmailMessage> findByOwnerEmpIdAndMessageId(String ownerEmpId, String messageId);

    /**
     * Check if email exists by messageId (global check across all employees)
     * Used for: Checking duplicates when fetching emails
     */
    boolean existsByMessageId(String messageId);

    /**
     * Count emails with attachments for specific employee
     * Used for: Statistics/analytics in getStatsForAccount()
     */
    long countByOwnerEmpIdAndHasAttachments(String ownerEmpId, boolean hasAttachments);

    /**
     * Delete all emails for employee
     */
    void deleteByOwnerEmpId(String ownerEmpId);

    // ========================================
    // READ/UNREAD QUERIES (BY EMPID)
    // ========================================

    /**
     * Find emails by employee and read status
     * Usage: findByOwnerEmpIdAndIsRead("ARGHSE004", false) → unread emails
     * Usage: findByOwnerEmpIdAndIsRead("ARGHSE004", true) → read emails
     */
    List<EmailMessage> findByOwnerEmpIdAndIsRead(String ownerEmpId, boolean isRead);

    /**
     * Count unread/read emails for employee
     */
    long countByOwnerEmpIdAndIsRead(String ownerEmpId, boolean isRead);

    // ========================================
    // STARRED QUERIES (BY EMPID)
    // ========================================

    /**
     * Find starred emails for employee
     */
    List<EmailMessage> findByOwnerEmpIdAndIsStarred(String ownerEmpId, boolean isStarred);

    /**
     * Count starred emails for employee
     */

    long countByOwnerEmpIdAndIsStarred(String ownerEmpId, boolean isStarred);

    // ========================================
    // SEARCH QUERIES (BY EMPID)
    // ========================================

    /**
     * Search by employee and subject (case-insensitive, partial match)
     */
    List<EmailMessage> findByOwnerEmpIdAndSubjectContainingIgnoreCase(String ownerEmpId, String subject);

    /**
     * Search by employee and sender email (case-insensitive, partial match)
     */
    List<EmailMessage> findByOwnerEmpIdAndFromEmailContainingIgnoreCase(String ownerEmpId, String fromEmail);

    /**
     * Search by employee and recipient email (case-insensitive, partial match)
     */
    List<EmailMessage> findByOwnerEmpIdAndToEmailContainingIgnoreCase(String ownerEmpId, String toEmail);

    // ========================================
    // DATE RANGE QUERIES (BY EMPID)
    // ========================================

    /**
     * Find emails received after a specific date for employee
     */
    List<EmailMessage> findByOwnerEmpIdAndReceivedDateAfter(String ownerEmpId, LocalDateTime afterDate);

    /**
     * Find emails received before a specific date for employee
     */
    List<EmailMessage> findByOwnerEmpIdAndReceivedDateBefore(String ownerEmpId, LocalDateTime beforeDate);

    /**
     * Find emails in date range for employee
     */
    List<EmailMessage> findByOwnerEmpIdAndReceivedDateBetween(String ownerEmpId, LocalDateTime startDate, LocalDateTime endDate);

    // ========================================
    // LABEL/CATEGORY QUERIES (BY EMPID)
    // ========================================

    /**
     * Find emails containing specific label for employee
     */
    List<EmailMessage> findByOwnerEmpIdAndLabelsContaining(String ownerEmpId, String label);

    // ========================================
    // ATTACHMENT QUERIES (BY EMPID)
    // ========================================

    /**
     * Find emails with attachments for employee
     */
    List<EmailMessage> findByOwnerEmpIdAndHasAttachments(String ownerEmpId, boolean hasAttachments);

    // ========================================
    // COMBINED QUERIES (BY EMPID)
    // ========================================

    /**
     * Find unread emails with specific label for employee
     */
    List<EmailMessage> findByOwnerEmpIdAndIsReadAndLabelsContaining(String ownerEmpId, boolean isRead, String label);

    /**
     * Find starred unread emails for employee
     */
    List<EmailMessage> findByOwnerEmpIdAndIsStarredAndIsRead(String ownerEmpId, boolean isStarred, boolean isRead);

    // ========================================
    // LEGACY METHODS (BACKWARD COMPATIBILITY - DEPRECATED)
    // ========================================

    /**
     * Find all emails for a specific owner
     * @deprecated Use findByOwnerEmpId() instead
     */
    List<EmailMessage> findByOwnerEmail(String ownerEmail);

    /**
     * Find email by owner and message ID
     * @deprecated Use findByOwnerEmpIdAndMessageId() instead
     */
    Optional<EmailMessage> findByOwnerEmailAndMessageId(String ownerEmail, String messageId);

    /**
     * Count total emails for owner
     * @deprecated Use countByOwnerEmpId() instead
     */
    long countByOwnerEmail(String ownerEmail);

    /**
     * Delete all emails for owner
     * @deprecated Use deleteByOwnerEmpId() instead
     */
    void deleteByOwnerEmail(String ownerEmail);

    /**
     * Find emails by read status
     * @deprecated Use findByOwnerEmpIdAndIsRead() instead
     */
    List<EmailMessage> findByOwnerEmailAndIsRead(String ownerEmail, boolean isRead);

    /**
     * Count unread/read emails
     * @deprecated Use countByOwnerEmpIdAndIsRead() instead
     */
    long countByOwnerEmailAndIsRead(String ownerEmail, boolean isRead);

    /**
     * Find starred emails
     * @deprecated Use findByOwnerEmpIdAndIsStarred() instead
     */
    List<EmailMessage> findByOwnerEmailAndIsStarred(String ownerEmail, boolean isStarred);

    /**
     * Count starred emails
     * @deprecated Use countByOwnerEmpIdAndIsStarred() instead
     */
    long countByOwnerEmailAndIsStarred(String ownerEmail, boolean isStarred);

    /**
     * Search by subject (case-insensitive, partial match)
     * @deprecated Use findByOwnerEmpIdAndSubjectContainingIgnoreCase() instead
     */
    List<EmailMessage> findByOwnerEmailAndSubjectContainingIgnoreCase(String ownerEmail, String subject);

    /**
     * Search by sender email (case-insensitive, partial match)
     * @deprecated Use findByOwnerEmpIdAndFromEmailContainingIgnoreCase() instead
     */
    List<EmailMessage> findByOwnerEmailAndFromEmailContainingIgnoreCase(String ownerEmail, String fromEmail);

    /**
     * Search by recipient email (case-insensitive, partial match)
     * @deprecated Use findByOwnerEmpIdAndToEmailContainingIgnoreCase() instead
     */
    List<EmailMessage> findByOwnerEmailAndToEmailContainingIgnoreCase(String ownerEmail, String toEmail);

    /**
     * Find emails received after a specific date
     * @deprecated Use findByOwnerEmpIdAndReceivedDateAfter() instead
     */
    List<EmailMessage> findByOwnerEmailAndReceivedDateAfter(String ownerEmail, LocalDateTime afterDate);

    /**
     * Find emails received before a specific date
     * @deprecated Use findByOwnerEmpIdAndReceivedDateBefore() instead
     */
    List<EmailMessage> findByOwnerEmailAndReceivedDateBefore(String ownerEmail, LocalDateTime beforeDate);

    /**
     * Find emails in date range
     * @deprecated Use findByOwnerEmpIdAndReceivedDateBetween() instead
     */
    List<EmailMessage> findByOwnerEmailAndReceivedDateBetween(String ownerEmail, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find emails containing specific label
     * @deprecated Use findByOwnerEmpIdAndLabelsContaining() instead
     */
    List<EmailMessage> findByOwnerEmailAndLabelsContaining(String ownerEmail, String label);

    /**
     * Find emails with attachments
     * @deprecated Use findByOwnerEmpIdAndHasAttachments() instead
     */
    List<EmailMessage> findByOwnerEmailAndHasAttachments(String ownerEmail, boolean hasAttachments);

    /**
     * Find unread emails with specific label
     * @deprecated Use findByOwnerEmpIdAndIsReadAndLabelsContaining() instead
     */
    List<EmailMessage> findByOwnerEmailAndIsReadAndLabelsContaining(String ownerEmail, boolean isRead, String label);

    /**
     * Find starred unread emails
     * @deprecated Use findByOwnerEmpIdAndIsStarredAndIsRead() instead
     */
    List<EmailMessage> findByOwnerEmailAndIsStarredAndIsRead(String ownerEmail, boolean isStarred, boolean isRead);
}
