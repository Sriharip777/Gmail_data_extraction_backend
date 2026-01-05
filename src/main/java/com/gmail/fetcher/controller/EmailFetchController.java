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
 * Email Fetch Controller - Complete Flow with Manager Access Support
 * Handles login status, fetch, and retrieve operations
 * Supports managers viewing employee emails
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
     * ✅ NEW: Validate if user can view target employee's emails
     * Returns the target empId if access is granted, throws exception otherwise
     */
    private String validateAndGetTargetEmpId(String requestedEmpId, HttpSession session) {
        String loggedInEmpId = (String) session.getAttribute("empId");
        String userRole = (String) session.getAttribute("userRole");

        if (loggedInEmpId == null || loggedInEmpId.isBlank()) {
            log.error("❌ No empId in session");
            throw new SecurityException("No session found. Please login.");
        }

        // If no empId requested or requesting own emails, allow
        if (requestedEmpId == null || requestedEmpId.isBlank() || requestedEmpId.equals(loggedInEmpId)) {
            log.info("✅ User {} viewing own emails", loggedInEmpId);
            return loggedInEmpId;
        }

        // If requesting someone else's emails, check if user is manager/HR/CEO
        if ("Manager".equals(userRole) || "HR".equals(userRole) || "CEO".equals(userRole)) {
            log.info("✅ {} ({}) is authorized to view {}'s emails", loggedInEmpId, userRole, requestedEmpId);
            return requestedEmpId;
        }

        // Access denied
        log.warn("⚠️ SECURITY: {} ({}) attempted to access {}'s emails - DENIED",
                loggedInEmpId, userRole, requestedEmpId);
        throw new SecurityException("Access denied. Only managers can view other employees' emails.");
    }

    /**
     * Check if user is logged in
     * GET /api/email/login-status
     */
    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> getLoginStatus(HttpServletRequest request) {
        log.info("========================================");
        log.info("📊 CHECKING LOGIN STATUS");
        log.info("========================================");

        Map<String, Object> response = new HashMap<>();

        // ✅ Get session first
        HttpSession session = request.getSession(false);

        if (session == null) {
            log.error("❌ NO SESSION FOUND!");
            response.put("loggedIn", false);
            response.put("message", "No session exists. Please login again.");
            response.put("reason", "session_not_found");
            log.info("========================================");
            return ResponseEntity.ok(response);
        }

        log.info("✅ SESSION EXISTS:");
        log.info("  Session ID: {}", session.getId());

        // ✅ Get empId from session
        String empId = (String) session.getAttribute("empId");

        if (empId == null || empId.isBlank()) {
            log.error("❌ NO EMPID IN SESSION!");
            log.info("  Available attributes: {}",
                    java.util.Collections.list(session.getAttributeNames()));

            response.put("loggedIn", false);
            response.put("message", "No employee session found. Please login.");
            response.put("reason", "empid_not_in_session");
            log.info("========================================");
            return ResponseEntity.ok(response);
        }

        log.info("✅ Found empId in session: {}", empId);

        // ✅ Check if THIS employee has a Gmail token
        GmailToken token = tokenRepository.findByEmpId(empId).orElse(null);

        if (token != null) {
            log.info("✅ Employee {} has Gmail connected: {}", empId, token.getGoogleEmail());

            long emailCount = emailRepository.countByOwnerEmpId(empId);

            response.put("loggedIn", true);
            response.put("email", token.getGoogleEmail());
            response.put("empId", empId);
            response.put("sessionId", session.getId());
            response.put("connectedAt", token.getCreatedAt());
            response.put("tokenExpiry", token.getAccessTokenExpiresAt());
            response.put("lastSynced", token.getLastSyncedAt());
            response.put("emailsInDB", emailCount);

            log.info("📊 STATS:");
            log.info("  Gmail: {}", token.getGoogleEmail());
            log.info("  Emails in DB: {}", emailCount);
            log.info("  Token Valid: Yes");
        } else {
            log.info("❌ Employee {} has NO Gmail connected", empId);

            response.put("loggedIn", false);
            response.put("message", "No Gmail connected for this employee");
            response.put("reason", "no_gmail_token");
            response.put("empId", empId);
        }

        log.info("========================================");
        return ResponseEntity.ok(response);
    }

    /**
     * ✅ UPDATED: Fetch and store emails with optional empId parameter
     * POST /api/email/fetch-and-store?empId=ARGHSE001 (optional)
     */
    @PostMapping("/fetch-and-store")
    public ResponseEntity<Map<String, Object>> fetchAndStoreEmails(
            @RequestParam(required = false) String empId,
            @RequestParam(defaultValue = "100") int maxResults,
            HttpServletRequest request) {

        log.info("========================================");
        log.info("📧 FETCH AND STORE EMAILS");
        log.info("========================================");

        HttpSession session = request.getSession(false);

        if (session == null) {
            log.error("❌ No session found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "No session. Please login first."
            ));
        }

        try {
            // ✅ Validate access and get target empId
            String targetEmpId = validateAndGetTargetEmpId(empId, session);

            log.info("✅ Target empId: {}", targetEmpId);

            // Get Gmail token for target employee
            GmailToken token = tokenRepository.findByEmpId(targetEmpId).orElse(null);

            if (token == null) {
                log.error("❌ No Gmail token found for empId: {}", targetEmpId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "message", "No Gmail account connected for employee " + targetEmpId,
                        "empId", targetEmpId
                ));
            }

            String googleEmail = token.getGoogleEmail();
            log.info("✅ Gmail connected: {}", googleEmail);
            log.info("  Max results: {}", maxResults);

            // Fetch emails for target employee
            int fetchedCount = gmailService.fetchAndSaveEmailsForAccount(targetEmpId, maxResults);
            long totalInDB = emailRepository.countByOwnerEmpId(targetEmpId);

            log.info("✅ Fetched and stored {} emails", fetchedCount);
            log.info("✅ Total in database: {}", totalInDB);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Emails fetched and stored successfully");
            response.put("empId", targetEmpId);
            response.put("account", googleEmail);
            response.put("fetchedCount", fetchedCount);
            response.put("totalInDB", totalInDB);
            response.put("timestamp", java.time.Instant.now());

            log.info("========================================");
            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            log.error("❌ Security Exception: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Error fetching emails", e);
            log.info("========================================");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch emails: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ UPDATED: Get all emails with pagination and optional empId parameter
     * GET /api/email/all?empId=ARGHSE001 (optional)
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllEmails(
            @RequestParam(required = false) String empId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Please login first"
            ));
        }

        try {
            // ✅ Validate access and get target empId
            String targetEmpId = validateAndGetTargetEmpId(empId, session);

            log.info("📧 Getting emails for empId: {} (page: {}, size: {})", targetEmpId, page, size);

            // Get Gmail email for logging
            GmailToken token = tokenRepository.findByEmpId(targetEmpId).orElse(null);
            String googleEmail = token != null ? token.getGoogleEmail() : "Unknown";

            List<EmailMessage> allEmails = emailRepository.findByOwnerEmpId(targetEmpId);

            // Pagination
            int start = page * size;
            int end = Math.min(start + size, allEmails.size());

            List<EmailMessage> paginatedEmails = allEmails.isEmpty() ?
                    allEmails : allEmails.subList(start, end);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("empId", targetEmpId);
            response.put("account", googleEmail);
            response.put("totalCount", allEmails.size());
            response.put("page", page);
            response.put("size", size);
            response.put("emails", paginatedEmails);

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            log.error("❌ Security Exception: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * ✅ UPDATED: Get unread emails with optional empId parameter
     * GET /api/email/unread?empId=ARGHSE001 (optional)
     */
    @GetMapping("/unread")
    public ResponseEntity<Map<String, Object>> getUnreadEmails(
            @RequestParam(required = false) String empId,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Please login first"
            ));
        }

        try {
            // ✅ Validate access and get target empId
            String targetEmpId = validateAndGetTargetEmpId(empId, session);

            GmailToken token = tokenRepository.findByEmpId(targetEmpId).orElse(null);
            String googleEmail = token != null ? token.getGoogleEmail() : "Unknown";

            List<EmailMessage> unreadEmails = emailRepository.findByOwnerEmpIdAndIsRead(targetEmpId, false);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("empId", targetEmpId);
            response.put("account", googleEmail);
            response.put("count", unreadEmails.size());
            response.put("emails", unreadEmails);

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * ✅ UPDATED: Search emails with optional empId parameter
     * GET /api/email/search?subject=test&empId=ARGHSE001 (empId optional)
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchEmails(
            @RequestParam(required = false) String empId,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String from,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Please login first"
            ));
        }

        try {
            // ✅ Validate access and get target empId
            String targetEmpId = validateAndGetTargetEmpId(empId, session);

            GmailToken token = tokenRepository.findByEmpId(targetEmpId).orElse(null);
            String googleEmail = token != null ? token.getGoogleEmail() : "Unknown";

            List<EmailMessage> results;

            if (subject != null && !subject.isBlank()) {
                results = emailRepository.findByOwnerEmpIdAndSubjectContainingIgnoreCase(targetEmpId, subject);
            } else if (from != null && !from.isBlank()) {
                results = emailRepository.findByOwnerEmpIdAndFromEmailContainingIgnoreCase(targetEmpId, from);
            } else {
                results = emailRepository.findByOwnerEmpId(targetEmpId);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("empId", targetEmpId);
            response.put("account", googleEmail);
            response.put("query", Map.of(
                    "subject", subject != null ? subject : "",
                    "from", from != null ? from : ""
            ));
            response.put("count", results.size());
            response.put("emails", results);

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * ✅ UPDATED: Get email statistics with optional empId parameter
     * GET /api/email/stats?empId=ARGHSE001 (optional)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getEmailStats(
            @RequestParam(required = false) String empId,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Please login first"
            ));
        }

        try {
            // ✅ Validate access and get target empId
            String targetEmpId = validateAndGetTargetEmpId(empId, session);

            GmailToken token = tokenRepository.findByEmpId(targetEmpId).orElse(null);
            String googleEmail = token != null ? token.getGoogleEmail() : "Unknown";

            long totalEmails = emailRepository.countByOwnerEmpId(targetEmpId);
            long unreadEmails = emailRepository.countByOwnerEmpIdAndIsRead(targetEmpId, false);
            long starredEmails = emailRepository.countByOwnerEmpIdAndIsStarred(targetEmpId, true);

            Map<String, Object> stats = new HashMap<>();
            stats.put("success", true);
            stats.put("empId", targetEmpId);
            stats.put("account", googleEmail);
            stats.put("totalEmails", totalEmails);
            stats.put("unreadEmails", unreadEmails);
            stats.put("starredEmails", starredEmails);
            stats.put("readEmails", totalEmails - unreadEmails);
            stats.put("lastSynced", token != null ? token.getLastSyncedAt() : null);
            stats.put("connectedAt", token != null ? token.getCreatedAt() : null);

            return ResponseEntity.ok(stats);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Helper method to get empId from session (legacy support)
     */
    private String getEmpIdFromSession(HttpSession session) {
        if (session == null) {
            return null;
        }
        String empId = (String) session.getAttribute("empId");
        return (empId != null && !empId.isBlank()) ? empId : null;
    }
}
