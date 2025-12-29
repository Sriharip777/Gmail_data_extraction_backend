package com.gmail.fetcher.controller;


import com.gmail.fetcher.entity.GmailToken;
import com.gmail.fetcher.repository.GmailTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test Controller for debugging and manual session management
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final GmailTokenRepository gmailTokenRepository;

    /**
     * Check what's in the database
     */
    @GetMapping("/check-db")
    public ResponseEntity<Map<String, Object>> checkDatabase() {
        log.info("========================================");
        log.info("🔍 CHECKING DATABASE");
        log.info("========================================");

        List<GmailToken> tokens = gmailTokenRepository.findAll();

        log.info("Found {} token(s) in database", tokens.size());

        List<Map<String, Object>> accountList = tokens.stream()
                .map(t -> {
                    Map<String, Object> account = new HashMap<>();
                    account.put("email", t.getGoogleEmail());
                    account.put("userId", t.getUserId());
                    account.put("createdAt", t.getCreatedAt());
                    account.put("lastSynced", t.getLastSyncedAt());
                    account.put("hasAccessToken", t.getAccessToken() != null && !t.getAccessToken().isEmpty());
                    account.put("hasRefreshToken", t.getRefreshToken() != null && !t.getRefreshToken().isEmpty());
                    account.put("tokenExpiry", t.getAccessTokenExpiresAt());

                    log.info("  ✅ Account: {}", t.getGoogleEmail());

                    return account;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalAccounts", tokens.size());
        response.put("accounts", accountList);

        log.info("========================================");

        return ResponseEntity.ok(response);
    }

    /**
     * Manually set session for an email (WORKAROUND)
     */
    @PostMapping("/manual-login")
    public ResponseEntity<Map<String, Object>> manualLogin(
            @RequestParam String email,
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("========================================");
        log.info("🔐 MANUAL LOGIN REQUEST");
        log.info("========================================");
        log.info("  Email: {}", email);

        // Verify email exists in database
        Optional<GmailToken> tokenOpt = gmailTokenRepository.findByUserId(email);

        if (tokenOpt.isEmpty()) {
            log.error("❌ Email not found in database: {}", email);
            log.info("========================================");

            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "Email not found in database: " + email,
                    "hint", "Please authorize first using /oauth/authorize"
            ));
        }

        GmailToken token = tokenOpt.get();
        log.info("✅ Found token for: {}", email);

        // Invalidate old session if exists
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            log.info("⚠️  Invalidating old session: {}", oldSession.getId());
            oldSession.invalidate();
        }

        // Create NEW session
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(30 * 60); // 30 minutes
        session.setAttribute("connectedEmail", email);

        log.info("========================================");
        log.info("✅ NEW SESSION CREATED:");
        log.info("  Session ID: {}", session.getId());
        log.info("  Email: {}", email);
        log.info("  Max Inactive: {} seconds", session.getMaxInactiveInterval());
        log.info("  Creation Time: {}", new Date(session.getCreationTime()));
        log.info("========================================");

        // Set cookie EXPLICITLY
        Cookie sessionCookie = new Cookie("GMAIL_FETCHER_SESSION", session.getId());
        sessionCookie.setPath("/");
        sessionCookie.setHttpOnly(false); // Allow JavaScript access for testing
        sessionCookie.setSecure(false); // HTTP (not HTTPS)
        sessionCookie.setMaxAge(30 * 60); // 30 minutes
        response.addCookie(sessionCookie);

        // ALSO set JSESSIONID (Spring's default)
        Cookie jsessionCookie = new Cookie("JSESSIONID", session.getId());
        jsessionCookie.setPath("/");
        jsessionCookie.setHttpOnly(true);
        jsessionCookie.setMaxAge(30 * 60);
        response.addCookie(jsessionCookie);

        log.info("✅ COOKIES SET:");
        log.info("  1. GMAIL_FETCHER_SESSION = {}", session.getId());
        log.info("  2. JSESSIONID = {}", session.getId());
        log.info("========================================");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", true);
        responseMap.put("message", "Session created successfully");
        responseMap.put("email", email);
        responseMap.put("sessionId", session.getId());
        responseMap.put("cookieName", "GMAIL_FETCHER_SESSION");
        responseMap.put("cookieValue", session.getId());
        responseMap.put("instructions", Map.of(
                "step1", "Copy the sessionId above",
                "step2", "In Postman, go to Cookies tab",
                "step3", "Add cookie: GMAIL_FETCHER_SESSION=" + session.getId(),
                "step4", "Domain: localhost",
                "step5", "Path: /",
                "step6", "Then test /api/email/login-status"
        ));
        responseMap.put("tokenInfo", Map.of(
                "createdAt", token.getCreatedAt(),
                "expiresAt", token.getAccessTokenExpiresAt(),
                "hasRefreshToken", token.getRefreshToken() != null
        ));

        log.info("✅ Manual login successful!");
        log.info("========================================");

        return ResponseEntity.ok(responseMap);
    }

    /**
     * Check current session status
     */
    @GetMapping("/session-info")
    public ResponseEntity<Map<String, Object>> getSessionInfo(HttpServletRequest request) {
        log.info("========================================");
        log.info("🔍 CHECKING SESSION INFO");
        log.info("========================================");

        HttpSession session = request.getSession(false);

        Map<String, Object> response = new HashMap<>();

        if (session == null) {
            log.error("❌ NO SESSION EXISTS");

            response.put("sessionExists", false);
            response.put("message", "No session found");

            // Check cookies
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                log.info("📝 Cookies received:");
                for (Cookie cookie : cookies) {
                    log.info("  - {}: {}", cookie.getName(), cookie.getValue());
                }

                List<Map<String, String>> cookieList = Arrays.stream(cookies)
                        .map(c -> Map.of("name", c.getName(), "value", c.getValue()))
                        .collect(Collectors.toList());
                response.put("cookies", cookieList);
            } else {
                log.warn("⚠️  No cookies in request");
                response.put("cookies", Collections.emptyList());
            }

            log.info("========================================");
            return ResponseEntity.ok(response);
        }

        log.info("✅ SESSION EXISTS:");
        log.info("  Session ID: {}", session.getId());
        log.info("  Created: {}", new Date(session.getCreationTime()));
        log.info("  Last Accessed: {}", new Date(session.getLastAccessedTime()));
        log.info("  Max Inactive: {} seconds", session.getMaxInactiveInterval());

        String email = (String) session.getAttribute("connectedEmail");
        log.info("  Connected Email: {}", email);

        List<String> attributes = Collections.list(session.getAttributeNames());
        log.info("  All Attributes: {}", attributes);

        response.put("sessionExists", true);
        response.put("sessionId", session.getId());
        response.put("createdAt", new Date(session.getCreationTime()));
        response.put("lastAccessed", new Date(session.getLastAccessedTime()));
        response.put("maxInactive", session.getMaxInactiveInterval());
        response.put("connectedEmail", email);
        response.put("allAttributes", attributes);

        // Check cookies
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            log.info("📝 Cookies received:");
            for (Cookie cookie : cookies) {
                log.info("  - {}: {}", cookie.getName(), cookie.getValue());
            }

            List<Map<String, String>> cookieList = Arrays.stream(cookies)
                    .map(c -> Map.of(
                            "name", c.getName(),
                            "value", c.getValue(),
                            "path", c.getPath() != null ? c.getPath() : "null",
                            "domain", c.getDomain() != null ? c.getDomain() : "null"
                    ))
                    .collect(Collectors.toList());
            response.put("cookies", cookieList);
        }

        log.info("========================================");

        return ResponseEntity.ok(response);
    }

    /**
     * Clear all sessions (logout all)
     */
    @PostMapping("/clear-session")
    public ResponseEntity<Map<String, String>> clearSession(HttpServletRequest request) {
        log.info("========================================");
        log.info("🗑️  CLEARING SESSION");
        log.info("========================================");

        HttpSession session = request.getSession(false);

        if (session != null) {
            String sessionId = session.getId();
            session.invalidate();
            log.info("✅ Session invalidated: {}", sessionId);
        } else {
            log.info("⚠️  No session to clear");
        }

        log.info("========================================");

        return ResponseEntity.ok(Map.of(
                "success", "true",
                "message", "Session cleared"
        ));
    }
}

