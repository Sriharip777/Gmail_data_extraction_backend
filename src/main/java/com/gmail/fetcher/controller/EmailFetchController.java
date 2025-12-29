package com.gmail.fetcher.controller;


import com.gmail.fetcher.entity.EmailMessage;
import com.gmail.fetcher.entity.GmailToken;
import com.gmail.fetcher.repository.EmailMessageRepository;
import com.gmail.fetcher.repository.GmailTokenRepository;
import com.gmail.fetcher.service.GmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Email Fetch Controller - Complete Flow
 * Handles login status, fetch, and retrieve operations
 */
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
@Slf4j
public class EmailFetchController {

    private final GmailService gmailService;
    private final EmailMessageRepository emailRepository;
    private final GmailTokenRepository tokenRepository;

    /**
     * Check if user is logged in
     * GET /api/email/login-status
     */
    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> getLoginStatus(HttpServletRequest request) {
        log.info("========================================");
        log.info("📊 CHECKING LOGIN STATUS");
        log.info("========================================");

        // Check session
        HttpSession session = request.getSession(false);

        if (session == null) {
            log.error("❌ NO SESSION FOUND!");
            log.info("========================================");
            return ResponseEntity.ok(Map.of(
                    "loggedIn", false,
                    "message", "No session exists. Please login again.",
                    "reason", "session_not_found"
            ));
        }

        log.info("✅ SESSION EXISTS:");
        log.info("  Session ID: {}", session.getId());
        log.info("  Created: {}", new java.util.Date(session.getCreationTime()));
        log.info("  Last Accessed: {}", new java.util.Date(session.getLastAccessedTime()));
        log.info("  Max Inactive: {} seconds", session.getMaxInactiveInterval());

        // Check email attribute
        String activeEmail = (String) session.getAttribute("connectedEmail");

        if (activeEmail == null || activeEmail.isEmpty()) {
            log.error("❌ SESSION EXISTS BUT NO EMAIL!");
            log.info("  Available attributes: {}",
                    java.util.Collections.list(session.getAttributeNames()));
            log.info("========================================");

            return ResponseEntity.ok(Map.of(
                    "loggedIn", false,
                    "message", "No Gmail account connected. Please login first.",
                    "reason", "email_not_in_session",
                    "sessionId", session.getId()
            ));
        }

        log.info("✅ USER LOGGED IN: {}", activeEmail);

        // Get additional info
        GmailToken token = tokenRepository.findByUserId(activeEmail).orElse(null);
        long emailCount = emailRepository.countByOwnerEmail(activeEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("loggedIn", true);
        response.put("email", activeEmail);
        response.put("sessionId", session.getId());
        response.put("emailsInDB", emailCount);

        if (token != null) {
            response.put("connectedAt", token.getCreatedAt());
            response.put("lastSynced", token.getLastSyncedAt());
            response.put("tokenExpiry", token.getAccessTokenExpiresAt());
        }

        log.info("📊 STATS:");
        log.info("  Emails in DB: {}", emailCount);
        log.info("  Token Valid: {}", token != null);
        log.info("========================================");

        return ResponseEntity.ok(response);
    }

    /**
     * Fetch and store emails
     * POST /api/email/fetch-and-store
     */
    @PostMapping("/fetch-and-store")
    public ResponseEntity<Map<String, Object>> fetchAndStoreEmails(
            @RequestParam(defaultValue = "100") int maxResults,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        String activeEmail = (session != null) ? (String) session.getAttribute("connectedEmail") : null;

        if (activeEmail == null || activeEmail.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Please login first. No active Gmail account.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        log.info("========================================");
        log.info("Fetching emails for: {}", activeEmail);
        log.info("Max results: {}", maxResults);
        log.info("========================================");

        try {
            int fetchedCount = gmailService.fetchAndSaveEmailsForAccount(activeEmail, maxResults);
            long totalInDB = emailRepository.countByOwnerEmail(activeEmail);

            log.info("✅ Fetched and stored {} emails", fetchedCount);
            log.info("✅ Total in database: {}", totalInDB);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Emails fetched and stored successfully");
            response.put("account", activeEmail);
            response.put("fetchedCount", fetchedCount);
            response.put("totalInDB", totalInDB);
            response.put("timestamp", java.time.Instant.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching emails", e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch emails: " + e.getMessage());
            error.put("account", activeEmail);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get all emails with pagination
     * GET /api/email/all
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        String activeEmail = (session != null) ? (String) session.getAttribute("connectedEmail") : null;

        if (activeEmail == null || activeEmail.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Please login first");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        log.info("Getting emails for: {} (page: {}, size: {})", activeEmail, page, size);

        List<EmailMessage> allEmails = emailRepository.findByOwnerEmail(activeEmail);

        // Pagination
        int start = page * size;
        int end = Math.min(start + size, allEmails.size());

        List<EmailMessage> paginatedEmails = allEmails.isEmpty() ?
                allEmails : allEmails.subList(start, Math.min(end, allEmails.size()));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("account", activeEmail);
        response.put("totalCount", allEmails.size());
        response.put("page", page);
        response.put("size", size);
        response.put("emails", paginatedEmails);

        return ResponseEntity.ok(response);
    }

    /**
     * Get unread emails
     * GET /api/email/unread
     */
    @GetMapping("/unread")
    public ResponseEntity<Map<String, Object>> getUnreadEmails(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String activeEmail = (session != null) ? (String) session.getAttribute("connectedEmail") : null;

        if (activeEmail == null || activeEmail.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Please login first");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        List<EmailMessage> unreadEmails = emailRepository.findByOwnerEmailAndIsRead(activeEmail, false);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("account", activeEmail);
        response.put("count", unreadEmails.size());
        response.put("emails", unreadEmails);

        return ResponseEntity.ok(response);
    }

    /**
     * Search emails
     * GET /api/email/search
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchEmails(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String from,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        String activeEmail = (session != null) ? (String) session.getAttribute("connectedEmail") : null;

        if (activeEmail == null || activeEmail.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Please login first");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        List<EmailMessage> results;

        if (subject != null && !subject.isBlank()) {
            results = emailRepository.findByOwnerEmailAndSubjectContainingIgnoreCase(activeEmail, subject);
        } else if (from != null && !from.isBlank()) {
            results = emailRepository.findByOwnerEmailAndFromEmailContainingIgnoreCase(activeEmail, from);
        } else {
            results = emailRepository.findByOwnerEmail(activeEmail);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("account", activeEmail);
        response.put("query", Map.of(
                "subject", subject != null ? subject : "",
                "from", from != null ? from : ""
        ));
        response.put("count", results.size());
        response.put("emails", results);

        return ResponseEntity.ok(response);
    }

    /**
     * Get email statistics
     * GET /api/email/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getEmailStats(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String activeEmail = (session != null) ? (String) session.getAttribute("connectedEmail") : null;

        if (activeEmail == null || activeEmail.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Please login first");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        long totalEmails = emailRepository.countByOwnerEmail(activeEmail);
        long unreadEmails = emailRepository.countByOwnerEmailAndIsRead(activeEmail, false);
        long starredEmails = emailRepository.countByOwnerEmailAndIsStarred(activeEmail, true);

        GmailToken token = tokenRepository.findByUserId(activeEmail).orElse(null);

        Map<String, Object> stats = new HashMap<>();
        stats.put("success", true);
        stats.put("account", activeEmail);
        stats.put("totalEmails", totalEmails);
        stats.put("unreadEmails", unreadEmails);
        stats.put("starredEmails", starredEmails);
        stats.put("readEmails", totalEmails - unreadEmails);
        stats.put("lastSynced", token != null ? token.getLastSyncedAt() : null);
        stats.put("connectedAt", token != null ? token.getCreatedAt() : null);

        return ResponseEntity.ok(stats);
    }
}
