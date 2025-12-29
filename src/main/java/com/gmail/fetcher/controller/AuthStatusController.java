package com.gmail.fetcher.controller;

import com.gmail.fetcher.repository.GmailTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auth status endpoint for frontend
 * UPDATED: Returns active account from session + list of all connected accounts
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthStatusController {

    private final GmailTokenRepository tokenRepository;

    /**
     * Get Gmail connection status
     * Returns active account and all connected accounts
     */
    @GetMapping("/auth/gmail/status")
    public Map<String, Object> status(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        // Get active account from session
        HttpSession session = request.getSession(false);
        String activeEmail = (session != null) ? (String) session.getAttribute("connectedEmail") : null;

        // Get all connected accounts
        long connectedCount = tokenRepository.count();
        List<String> connectedEmails = tokenRepository.findAll()
                .stream()
                .map(token -> token.getGoogleEmail())
                .collect(java.util.stream.Collectors.toList());

        // Build response
        response.put("connected", connectedCount > 0);
        response.put("hasActiveAccount", activeEmail != null);
        response.put("activeEmail", activeEmail);
        response.put("connectedAccountsCount", connectedCount);
        response.put("connectedAccounts", connectedEmails);

        log.debug("Auth status: connected={}, active={}", connectedCount > 0, activeEmail);

        return response;
    }
}
