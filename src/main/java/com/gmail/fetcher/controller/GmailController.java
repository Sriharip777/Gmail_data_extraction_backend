package com.gmail.fetcher.controller;

import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.EmailFilterDTO;
import com.gmail.fetcher.dto.GmailCredentialsDTO;
import com.gmail.fetcher.entity.EmailMessage;
import com.gmail.fetcher.service.GmailService;
import com.gmail.fetcher.util.MongoEmailUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Gmail operations
 */
@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
@Slf4j
public class GmailController {

    private final GmailService gmailService;
    private final MongoEmailUtil mongoEmailUtil;

    /**
     * Fetch all emails from Gmail and save to database
     * POST /api/gmail/fetch-all
     */
    @PostMapping("/fetch-all")
    public ResponseEntity<Map<String, Object>> fetchAllEmails(
            @Valid @RequestBody GmailCredentialsDTO credentials) {

        log.info("Fetching all emails for user: {}", credentials.getEmail());

        List<EmailDTO> emails = gmailService.fetchAllEmails(credentials);
        gmailService.saveEmailsToDatabase(emails);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Emails fetched successfully");
        response.put("count", emails.size());
        response.put("emails", emails);

        return ResponseEntity.ok(response);
    }

    /**
     * Fetch filtered emails from Gmail
     * POST /api/gmail/fetch-filtered
     */
    @PostMapping("/fetch-filtered")
    public ResponseEntity<Map<String, Object>> fetchFilteredEmails(
            @Valid @RequestBody GmailCredentialsDTO credentials,
            @RequestBody EmailFilterDTO filter) {

        log.info("Fetching filtered emails for user: {}", credentials.getEmail());

        List<EmailDTO> emails = gmailService.fetchEmailsWithFilter(credentials, filter);
        gmailService.saveEmailsToDatabase(emails);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Filtered emails fetched successfully");
        response.put("count", emails.size());
        response.put("emails", emails);

        return ResponseEntity.ok(response);
    }

    /**
     * Fetch single email by ID
     * GET /api/gmail/email/{messageId}
     */
    @PostMapping("/email/{messageId}")
    public ResponseEntity<EmailDTO> fetchEmailById(
            @Valid @RequestBody GmailCredentialsDTO credentials,
            @PathVariable String messageId) {

        log.info("Fetching email with ID: {}", messageId);

        EmailDTO email = gmailService.fetchEmailById(credentials, messageId);
        return ResponseEntity.ok(email);
    }

    /**
     * Search emails in database
     * POST /api/gmail/search-database
     */
    @PostMapping("/search-database")
    public ResponseEntity<List<EmailDTO>> searchInDatabase(
            @RequestBody EmailFilterDTO filter) {

        log.info("Searching emails in database");

        List<EmailDTO> emails = gmailService.searchEmailsInDatabase(filter);
        return ResponseEntity.ok(emails);
    }

    /**
     * Get latest N emails from database
     * GET /api/gmail/latest/{limit}
     */
    @GetMapping("/latest/{limit}")
    public ResponseEntity<List<EmailMessage>> getLatestEmails(
            @PathVariable int limit) {

        log.info("Getting latest {} emails", limit);

        List<EmailMessage> emails = mongoEmailUtil.getLatestEmails(limit);
        return ResponseEntity.ok(emails);
    }

    /**
     * Mark email as read
     * PUT /api/gmail/mark-read/{messageId}
     */
    @PutMapping("/mark-read/{messageId}")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable String messageId) {

        log.info("Marking email as read: {}", messageId);

        mongoEmailUtil.markAsRead(messageId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Email marked as read");
        response.put("messageId", messageId);

        return ResponseEntity.ok(response);
    }

    /**
     * Bulk mark emails as read
     * PUT /api/gmail/bulk-mark-read
     */
    @PutMapping("/bulk-mark-read")
    public ResponseEntity<Map<String, Object>> bulkMarkAsRead(
            @RequestBody List<String> messageIds) {

        log.info("Bulk marking {} emails as read", messageIds.size());

        mongoEmailUtil.bulkMarkAsRead(messageIds);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Emails marked as read");
        response.put("count", messageIds.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Mark email as starred/unstarred
     * PUT /api/gmail/star/{messageId}
     */
    @PutMapping("/star/{messageId}")
    public ResponseEntity<Map<String, Object>> toggleStar(
            @PathVariable String messageId,
            @RequestParam boolean starred) {

        log.info("Setting star status for email: {} to {}", messageId, starred);

        mongoEmailUtil.markAsStarred(messageId, starred);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Email " + (starred ? "starred" : "unstarred"));
        response.put("messageId", messageId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get email statistics
     * GET /api/gmail/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.info("Getting email statistics");

        Map<String, Object> stats = mongoEmailUtil.getStorageStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get email count by sender
     * GET /api/gmail/stats/by-sender
     */
    @GetMapping("/stats/by-sender")
    public ResponseEntity<Map<String, Long>> getEmailCountBySender() {
        log.info("Getting email count by sender");

        Map<String, Long> stats = mongoEmailUtil.getEmailCountBySender();
        return ResponseEntity.ok(stats);
    }

    /**
     * Full-text search
     * GET /api/gmail/search?q={query}
     */
    @GetMapping("/search")
    public ResponseEntity<List<EmailMessage>> fullTextSearch(
            @RequestParam String q) {

        log.info("Performing full-text search: {}", q);

        List<EmailMessage> results = mongoEmailUtil.searchFullText(q);
        return ResponseEntity.ok(results);
    }

    /**
     * Delete old emails
     * DELETE /api/gmail/cleanup
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> deleteOldEmails(
            @RequestParam String beforeDate) {

        log.info("Deleting emails before: {}", beforeDate);

        LocalDateTime date = LocalDateTime.parse(beforeDate);
        long deletedCount = mongoEmailUtil.deleteOldEmails(date);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Old emails deleted");
        response.put("deletedCount", deletedCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Get total email count
     * GET /api/gmail/count
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getTotalCount() {
        log.info("Getting total email count");

        long count = mongoEmailUtil.getTotalEmailCount();

        Map<String, Object> response = new HashMap<>();
        response.put("totalEmails", count);

        return ResponseEntity.ok(response);
    }

    /**
     * Sync emails from Gmail
     * POST /api/gmail/sync
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncEmails(
            @Valid @RequestBody GmailCredentialsDTO credentials) {

        log.info("Starting email sync for user: {}", credentials.getEmail());

        gmailService.syncEmailsFromGmail(credentials);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Email sync completed successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     * GET /api/gmail/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Gmail Fetcher API");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Manual trigger to fetch and save emails
     * POST /api/gmail/fetch-and-save
     */
    @PostMapping("/fetch-and-save")
    public ResponseEntity<Map<String, Object>> fetchAndSaveEmails(
            @Valid @RequestBody GmailCredentialsDTO credentials) {

        log.info("Manual fetch triggered for user: {}", credentials.getEmail());

        try {
            // Fetch emails
            List<EmailDTO> emails = gmailService.fetchAllEmails(credentials);

            // Save to database
            gmailService.saveEmailsToDatabase(emails);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Emails fetched and saved successfully");
            response.put("count", emails.size());
            response.put("totalInDatabase", mongoEmailUtil.getTotalEmailCount());
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching and saving emails: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch and save emails");
            error.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Fetch filtered emails and save to database
     * POST /api/gmail/fetch-filtered-and-save
     */
    @PostMapping("/fetch-filtered-and-save")
    public ResponseEntity<Map<String, Object>> fetchFilteredAndSaveEmails(
            @Valid @RequestBody GmailCredentialsDTO credentials,
            @RequestBody EmailFilterDTO filter) {

        log.info("Filtered fetch triggered for user: {}", credentials.getEmail());

        try {
            // Fetch filtered emails
            List<EmailDTO> emails = gmailService.fetchEmailsWithFilter(credentials, filter);

            // Save to database
            gmailService.saveEmailsToDatabase(emails);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Filtered emails fetched and saved successfully");
            response.put("count", emails.size());
            response.put("totalInDatabase", mongoEmailUtil.getTotalEmailCount());
            response.put("filter", filter);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching and saving filtered emails: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch and save filtered emails");
            error.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

}

