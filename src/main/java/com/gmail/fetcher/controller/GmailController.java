package com.gmail.fetcher.controller;

import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.EmailFilterDTO;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for Gmail operations
 * UPDATED: Full support for multiple Gmail accounts with session management
 */
@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
@Slf4j
public class GmailController {

    private final GmailService gmailService;
    private final EmailMessageRepository emailRepository;
    private final GmailTokenRepository gmailTokenRepository;

    /**
     * Get list of all connected Gmail accounts
     * GET /api/gmail/accounts
     */
    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> getConnectedAccounts() {
        log.info("Fetching all connected Gmail accounts");

        List<GmailToken> tokens = gmailTokenRepository.findAll();

        List<Map<String, Object>> accounts = tokens.stream()
                .map(token -> {
                    Map<String, Object> account = new HashMap<>();
                    account.put("email", token.getGoogleEmail());
                    account.put("userId", token.getUserId());
                    account.put("connectedAt", token.getCreatedAt());
                    account.put("lastSynced", token.getLastSyncedAt());
                    account.put("emailCount", emailRepository.countByOwnerEmail(token.getGoogleEmail()));
                    return account;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", accounts.size());
        response.put("accounts", accounts);

        return ResponseEntity.ok(response);
    }

    /**
     * Set active Gmail account in session
     * POST /api/gmail/set-active
     */
    @PostMapping("/set-active")
    public ResponseEntity<Map<String, Object>> setActiveAccount(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String email = request.get("email");

        if (email == null || email.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Email is required");
            return ResponseEntity.badRequest().body(error);
        }

        // Verify account exists
        GmailToken token = gmailTokenRepository.findByUserId(email)
                .orElse(null);

        if (token == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Gmail account not found: " + email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        // Set in session
        HttpSession session = httpRequest.getSession();
        session.setAttribute("connectedEmail", email);

        log.info("Active Gmail account set to: {}", email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Active account set successfully");
        response.put("activeEmail", email);

        return ResponseEntity.ok(response);
    }

    /**
     * Get current active Gmail account
     * GET /api/gmail/active-account
     */
    @GetMapping("/active-account")
    public ResponseEntity<Map<String, Object>> getActiveAccount(HttpServletRequest request) {
        HttpSession session = request.getSession();
        String activeEmail = (String) session.getAttribute("connectedEmail");

        Map<String, Object> response = new HashMap<>();
        if (activeEmail != null) {
            response.put("success", true);
            response.put("activeEmail", activeEmail);
            response.put("emailCount", emailRepository.countByOwnerEmail(activeEmail));
        } else {
            response.put("success", false);
            response.put("message", "No active Gmail account");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Fetch emails for active account (MAX 100)
     * POST /api/gmail/fetch
     */
    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchEmailsForActiveAccount(HttpServletRequest request) {

        HttpSession session = request.getSession();
        String activeEmail = (String) session.getAttribute("connectedEmail");

        if (activeEmail == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "No Gmail account is active. Please set active account first.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        log.info("Fetching emails for active account: {}", activeEmail);

        try {
            // Fetch max 100 emails for this account
            int fetchedCount = gmailService.fetchAndSaveEmailsForAccount(activeEmail, 100);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Emails fetched successfully");
            response.put("account", activeEmail);
            response.put("fetchedCount", fetchedCount);
            response.put("totalInDB", emailRepository.countByOwnerEmail(activeEmail));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching emails: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch emails: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get emails for active account
     * GET /api/gmail/emails
     */
    @GetMapping("/emails")
    public ResponseEntity<Map<String, Object>> getEmailsForActiveAccount(HttpServletRequest request) {

        HttpSession session = request.getSession();
        String activeEmail = (String) session.getAttribute("connectedEmail");

        if (activeEmail == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "No active Gmail account");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        List<EmailMessage> emails = emailRepository.findByOwnerEmail(activeEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("account", activeEmail);
        response.put("count", emails.size());
        response.put("emails", emails);

        return ResponseEntity.ok(response);
    }

    /**
     * Disconnect Gmail account
     * DELETE /api/gmail/disconnect/{email}
     */
    @DeleteMapping("/disconnect/{email}")
    public ResponseEntity<Map<String, Object>> disconnectAccount(
            @PathVariable String email,
            HttpServletRequest request) {

        log.info("Disconnecting Gmail account: {}", email);

        // Remove tokens
        gmailTokenRepository.deleteByGoogleEmail(email);

        // Remove emails
        emailRepository.deleteByOwnerEmail(email);

        // Clear session if this was active account
        HttpSession session = request.getSession();
        String activeEmail = (String) session.getAttribute("connectedEmail");
        if (email.equals(activeEmail)) {
            session.removeAttribute("connectedEmail");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Account disconnected successfully");
        response.put("email", email);

        return ResponseEntity.ok(response);
    }

    /**
     * Get statistics for active account
     * GET /api/gmail/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatsForActiveAccount(HttpServletRequest request) {

        HttpSession session = request.getSession();
        String activeEmail = (String) session.getAttribute("connectedEmail");

        if (activeEmail == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "No active Gmail account");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        Map<String, Object> stats = gmailService.getStatsForAccount(activeEmail);
        stats.put("account", activeEmail);

        return ResponseEntity.ok(stats);
    }

    /**
     * Health check
     * GET /api/gmail/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Gmail Fetcher API - Multi-Account Support");
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(response);
    }
}
