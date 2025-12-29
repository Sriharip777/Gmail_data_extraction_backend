package com.gmail.fetcher.controller;

import com.gmail.fetcher.config.GmailOAuthProperties;
import com.gmail.fetcher.util.GmailAuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth Controller
 * UPDATED: Added config test endpoint
 */
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final GmailAuthUtil gmailAuthUtil;
    private final GmailOAuthProperties oauthProperties;

    /**
     * Get authorization URL
     * GET /oauth/authorize
     */
    @GetMapping("/authorize")
    public Map<String, String> getAuthUrl() {
        try {
            log.info("Generating OAuth authorization URL...");
            String authUrl = gmailAuthUtil.getAuthorizationUrl();

            Map<String, String> response = new HashMap<>();
            response.put("authorizationUrl", authUrl);
            response.put("message", "Open this URL in browser to authorize Gmail access");

            log.info("✅ Authorization URL generated successfully");
            return response;

        } catch (Exception e) {
            log.error("❌ Error generating authorization URL", e);

            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate authorization URL");
            error.put("details", e.getMessage());
            return error;
        }
    }

    /**
     * Test OAuth configuration
     * GET /oauth/config-test
     */
    @GetMapping("/config-test")
    public Map<String, String> testConfig() {
        Map<String, String> config = new HashMap<>();

        config.put("clientIdSet", oauthProperties.getClientId() != null ? "YES" : "NO");
        config.put("clientIdPreview", oauthProperties.getClientId() != null ?
                oauthProperties.getClientId().substring(0, Math.min(20, oauthProperties.getClientId().length())) + "..." : "NULL");
        config.put("clientSecretSet", oauthProperties.getClientSecret() != null ? "YES" : "NO");
        config.put("redirectUri", oauthProperties.getRedirectUri());

        return config;
    }
}
