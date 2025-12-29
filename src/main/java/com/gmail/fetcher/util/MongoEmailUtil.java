package com.gmail.fetcher.util;


import com.gmail.fetcher.entity.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Utility class for MongoDB email operations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoEmailUtil {

    private final MongoTemplate mongoTemplate;

    /**
     * Mark single email as read
     */
    public void markAsRead(String messageId) {
        Query query = new Query(Criteria.where("messageId").is(messageId));
        Update update = new Update()
                .set("isRead", true)
                .set("updatedAt", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, EmailMessage.class);
        log.info("Marked email {} as read", messageId);
    }

    /**
     * Mark multiple emails as read
     */
    public void bulkMarkAsRead(List<String> messageIds) {
        Query query = new Query(Criteria.where("messageId").in(messageIds));
        Update update = new Update()
                .set("isRead", true)
                .set("updatedAt", LocalDateTime.now());

        mongoTemplate.updateMulti(query, update, EmailMessage.class);
        log.info("Marked {} emails as read", messageIds.size());
    }

    /**
     * Mark email as starred
     */
    public void markAsStarred(String messageId, boolean starred) {
        Query query = new Query(Criteria.where("messageId").is(messageId));
        Update update = new Update()
                .set("isStarred", starred)
                .set("updatedAt", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, EmailMessage.class);
        log.info("Marked email {} as {}", messageId, starred ? "starred" : "unstarred");
    }

    /**
     * Get latest N emails
     */
    public List<EmailMessage> getLatestEmails(int limit) {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "receivedDate"))
                .limit(limit);

        List<EmailMessage> emails = mongoTemplate.find(query, EmailMessage.class);
        log.info("Retrieved {} latest emails", emails.size());

        return emails;
    }

    /**
     * Get unread email count
     */
    public long getUnreadCount() {
        Query query = new Query(Criteria.where("isRead").is(false));
        long count = mongoTemplate.count(query, EmailMessage.class);
        log.debug("Unread email count: {}", count);

        return count;
    }

    /**
     * Get email count by sender
     */
    public Map<String, Long> getEmailCountBySender() {
        Aggregation aggregation = newAggregation(
                group("fromEmail").count().as("count"),
                sort(Sort.Direction.DESC, "count")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "email_messages", Map.class);

        Map<String, Long> senderCounts = new HashMap<>();
        for (Map result : results.getMappedResults()) {
            String sender = (String) result.get("_id");
            Integer count = (Integer) result.get("count");
            senderCounts.put(sender, count.longValue());
        }

        log.info("Retrieved email counts for {} senders", senderCounts.size());
        return senderCounts;
    }

    /**
     * Get emails grouped by date
     */
    public Map<String, Long> getEmailCountByDate() {
        Aggregation aggregation = newAggregation(
                project("receivedDate")
                        .andExpression("dateToString('%Y-%m-%d', receivedDate)").as("date"),
                group("date").count().as("count"),
                sort(Sort.Direction.DESC, "_id")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "email_messages", Map.class);

        Map<String, Long> dateCounts = new HashMap<>();
        for (Map result : results.getMappedResults()) {
            String date = (String) result.get("_id");
            Integer count = (Integer) result.get("count");
            dateCounts.put(date, count.longValue());
        }

        return dateCounts;
    }

    /**
     * Delete old emails before a specific date
     */
    /**
     * Delete old emails for specific owner
     */
    public long deleteOldEmailsForOwner(String ownerEmail, LocalDateTime beforeDate) {
        Query query = new Query();
        query.addCriteria(Criteria.where("ownerEmail").is(ownerEmail));
        query.addCriteria(Criteria.where("receivedDate").lt(beforeDate));

        long deletedCount = mongoTemplate.remove(query, EmailMessage.class).getDeletedCount();

        log.info("Deleted {} old emails for {} before {}", deletedCount, ownerEmail, beforeDate);
        return deletedCount;
    }


    /**
     * Get total email count
     */
    public long getTotalEmailCount() {
        long count = mongoTemplate.count(new Query(), EmailMessage.class);
        log.debug("Total email count: {}", count);
        return count;
    }

    /**
     * Full-text search across subject and body
     */
    public List<EmailMessage> searchFullText(String searchTerm) {
        Query query = new Query();
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("subject").regex(searchTerm, "i"),
                Criteria.where("bodyText").regex(searchTerm, "i"),
                Criteria.where("fromEmail").regex(searchTerm, "i"),
                Criteria.where("toEmail").regex(searchTerm, "i")
        );
        query.addCriteria(criteria);

        List<EmailMessage> results = mongoTemplate.find(query, EmailMessage.class);
        log.info("Full-text search for '{}' returned {} results", searchTerm, results.size());

        return results;
    }

    /**
     * Get storage statistics
     */
    public Map<String, Object> getStorageStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalEmails", getTotalEmailCount());
        stats.put("unreadEmails", getUnreadCount());

        Query starredQuery = new Query(Criteria.where("isStarred").is(true));
        stats.put("starredEmails", mongoTemplate.count(starredQuery, EmailMessage.class));

        Query attachmentQuery = new Query(Criteria.where("hasAttachments").is(true));
        stats.put("emailsWithAttachments", mongoTemplate.count(attachmentQuery, EmailMessage.class));

        log.info("Storage stats: {}", stats);
        return stats;
    }
}

