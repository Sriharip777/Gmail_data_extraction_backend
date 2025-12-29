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
 */
@Repository
public interface EmailMessageRepository extends MongoRepository<EmailMessage, String> {

    // ========================================
    // BASIC QUERIES
    // ========================================

    /**
     * Find all emails for a specific owner
     */
    List<EmailMessage> findByOwnerEmail(String ownerEmail);

    /**
     * Find email by owner and message ID
     */
    Optional<EmailMessage> findByOwnerEmailAndMessageId(String ownerEmail, String messageId);

    /**
     * Count total emails for owner
     */
    long countByOwnerEmail(String ownerEmail);

    /**
     * Delete all emails for owner
     */
    void deleteByOwnerEmail(String ownerEmail);

    // ========================================
    // READ/UNREAD QUERIES
    // ========================================

    /**
     * Find emails by read status
     * Usage: findByOwnerEmailAndIsRead("user@gmail.com", false) → unread emails
     * Usage: findByOwnerEmailAndIsRead("user@gmail.com", true) → read emails
     */
    List<EmailMessage> findByOwnerEmailAndIsRead(String ownerEmail, boolean isRead);

    /**
     * Count unread/read emails
     */
    long countByOwnerEmailAndIsRead(String ownerEmail, boolean isRead);

    // ========================================
    // STARRED QUERIES
    // ========================================

    /**
     * Find starred emails
     */
    List<EmailMessage> findByOwnerEmailAndIsStarred(String ownerEmail, boolean isStarred);

    /**
     * Count starred emails
     */
    long countByOwnerEmailAndIsStarred(String ownerEmail, boolean isStarred);

    // ========================================
    // SEARCH QUERIES
    // ========================================

    /**
     * Search by subject (case-insensitive, partial match)
     */
    List<EmailMessage> findByOwnerEmailAndSubjectContainingIgnoreCase(String ownerEmail, String subject);

    /**
     * Search by sender email (case-insensitive, partial match)
     */
    List<EmailMessage> findByOwnerEmailAndFromEmailContainingIgnoreCase(String ownerEmail, String fromEmail);

    /**
     * Search by recipient email (case-insensitive, partial match)
     */
    List<EmailMessage> findByOwnerEmailAndToEmailContainingIgnoreCase(String ownerEmail, String toEmail);

    // ========================================
    // DATE RANGE QUERIES
    // ========================================

    /**
     * Find emails received after a specific date
     */
    List<EmailMessage> findByOwnerEmailAndReceivedDateAfter(String ownerEmail, LocalDateTime afterDate);

    /**
     * Find emails received before a specific date
     */
    List<EmailMessage> findByOwnerEmailAndReceivedDateBefore(String ownerEmail, LocalDateTime beforeDate);

    /**
     * Find emails in date range
     */
    List<EmailMessage> findByOwnerEmailAndReceivedDateBetween(String ownerEmail, LocalDateTime startDate, LocalDateTime endDate);

    // ========================================
    // LABEL/CATEGORY QUERIES
    // ========================================

    /**
     * Find emails containing specific label
     */
    List<EmailMessage> findByOwnerEmailAndLabelsContaining(String ownerEmail, String label);

    // ========================================
    // ATTACHMENT QUERIES
    // ========================================

    /**
     * Find emails with attachments
     */
    List<EmailMessage> findByOwnerEmailAndHasAttachments(String ownerEmail, boolean hasAttachments);

    // ========================================
    // COMBINED QUERIES
    // ========================================

    /**
     * Find unread emails with specific label
     */
    List<EmailMessage> findByOwnerEmailAndIsReadAndLabelsContaining(String ownerEmail, boolean isRead, String label);

    /**
     * Find starred unread emails
     */
    List<EmailMessage> findByOwnerEmailAndIsStarredAndIsRead(String ownerEmail, boolean isStarred, boolean isRead);
}
