package com.gmail.fetcher.repository;


import com.gmail.fetcher.entity.EmailMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for EmailMessage
 */
@Repository
public interface EmailMessageRepository extends MongoRepository<EmailMessage, String> {

    // Find by message ID
    Optional<EmailMessage> findByMessageId(String messageId);

    // Find by sender email
    List<EmailMessage> findByFromEmailContaining(String fromEmail);

    // Find by subject
    List<EmailMessage> findBySubjectContaining(String subject);

    // Find by date range
    List<EmailMessage> findByReceivedDateBetween(LocalDateTime start, LocalDateTime end);

    // Find by read status
    List<EmailMessage> findByIsRead(Boolean isRead);

    // Find by labels
    List<EmailMessage> findByLabelsContaining(String label);

    // Find unread emails
    @Query("{ 'isRead': false }")
    List<EmailMessage> findUnreadEmails();

    // Find starred emails
    @Query("{ 'isStarred': true }")
    List<EmailMessage> findStarredEmails();

    // Custom filter query
    @Query("{ 'fromEmail': { $regex: ?0, $options: 'i' }, " +
            "'subject': { $regex: ?1, $options: 'i' }, " +
            "'receivedDate': { $gte: ?2, $lte: ?3 } }")
    List<EmailMessage> findByCustomFilter(String from, String subject,
                                          LocalDateTime startDate,
                                          LocalDateTime endDate);

    // Find emails after a specific date
    @Query("{ 'receivedDate': { $gte: ?0 } }")
    List<EmailMessage> findEmailsAfterDate(LocalDateTime date);

    // Find unread emails by labels
    @Query("{ 'isRead': false, 'labels': { $in: ?0 } }")
    List<EmailMessage> findUnreadEmailsByLabels(List<String> labels);

    // Count emails by sender
    @Query(value = "{ 'fromEmail': ?0 }", count = true)
    Long countByFromEmail(String fromEmail);

    // Delete old emails
    void deleteByReceivedDateBefore(LocalDateTime date);

    // Paginated search
    Page<EmailMessage> findByFromEmailContaining(String fromEmail, Pageable pageable);

    // Check if message exists
    boolean existsByMessageId(String messageId);
}
