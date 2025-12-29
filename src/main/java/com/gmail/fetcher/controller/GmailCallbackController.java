package com.gmail.fetcher.controller;

import com.gmail.fetcher.config.GmailOAuthProperties;
import com.gmail.fetcher.config.GmailProperties;
import com.gmail.fetcher.entity.GmailToken;
import com.gmail.fetcher.repository.GmailTokenRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Instant;

/**
 * OAuth2 Callback Controller
 * COMPLETE FIX: Properly uses Google Credential to get email
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GmailCallbackController {

    private final GmailOAuthProperties oauthProperties;
    private final GmailProperties gmailProperties;
    private final GmailTokenRepository tokenRepository;

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String USER_ID = "me";

    @Value("${frontend.redirect-url:http://localhost:5173/connected}")
    private String frontendRedirectUrl;

    @GetMapping("/oauth2callback")
    public RedirectView handleGoogleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request) {

        log.info("========================================");
        log.info("OAuth2 Callback Received");
        log.info("========================================");

        // Handle OAuth errors
        if (error != null) {
            log.error("❌ OAuth error from Google: {}", error);
            return new RedirectView(frontendRedirectUrl + "?error=oauth_" + error);
        }

        if (code == null || code.isBlank()) {
            log.error("❌ No authorization code received");
            return new RedirectView(frontendRedirectUrl + "?error=no_code");
        }

        try {
            // Create HTTP transport
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // Build Google client secrets
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                    .setClientId(oauthProperties.getClientId())
                    .setClientSecret(oauthProperties.getClientSecret());

            GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

            // Create authorization flow
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    JSON_FACTORY,
                    clientSecrets,
                    gmailProperties.getScopesList()
            )
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            // Exchange authorization code for tokens
            log.info("Exchanging authorization code for tokens...");
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(oauthProperties.getRedirectUri())
                    .execute();

            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();
            Long expiresInSeconds = tokenResponse.getExpiresInSeconds();

            log.info("✅ Tokens received successfully");
            log.info("  Access Token: {}", accessToken != null ? "Present (" + accessToken.length() + " chars)" : "MISSING");
            log.info("  Refresh Token: {}", refreshToken != null ? "Present" : "MISSING");
            log.info("  Expires In: {} seconds", expiresInSeconds);

            // CRITICAL FIX: Create proper credential and build Gmail service
            Credential credential = flow.createAndStoreCredential(tokenResponse, USER_ID);

            log.info("Building Gmail service...");
            Gmail gmailService = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(gmailProperties.getApplicationName())
                    .build();

            // Get email from Gmail Profile API (most reliable method)
            log.info("Fetching user profile from Gmail API...");
            String googleEmail = gmailService.users()
                    .getProfile(USER_ID)
                    .execute()
                    .getEmailAddress();

            if (googleEmail == null || googleEmail.isBlank()) {
                log.error("❌ Gmail API returned null or empty email");
                return new RedirectView(frontendRedirectUrl + "?error=email_not_found");
            }

            log.info("✅ Successfully retrieved email from Gmail API: {}", googleEmail);

            // Save token to database
            saveTokenToDatabase(googleEmail, accessToken, refreshToken, expiresInSeconds);

            // Set active account in session
            HttpSession session = request.getSession();
            session.setAttribute("connectedEmail", googleEmail);
            log.info("✅ Set {} as active account in session", googleEmail);

            log.info("========================================");
            log.info("✅ OAuth flow completed successfully");
            log.info("========================================");

            return new RedirectView(frontendRedirectUrl + "?email=" + googleEmail + "&status=success");

        } catch (Exception e) {
            log.error("========================================");
            log.error("❌ Error in OAuth callback", e);
            log.error("========================================");

            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 100) {
                errorMessage = errorMessage.substring(0, 100);
            }

            return new RedirectView(frontendRedirectUrl + "?error=callback_failed&details=" +
                    java.net.URLEncoder.encode(errorMessage != null ? errorMessage : "Unknown error",
                            java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * Save token to database
     */
    private void saveTokenToDatabase(String googleEmail, String accessToken,
                                     String refreshToken, Long expiresInSeconds) {

        String userId = googleEmail;
        Instant now = Instant.now();
        Instant expiresAt = (expiresInSeconds != null) ? now.plusSeconds(expiresInSeconds) : null;

        // Find existing token or create new
        GmailToken token = tokenRepository.findByUserId(userId)
                .orElse(GmailToken.builder().userId(userId).build());

        token.setGoogleEmail(googleEmail);
        token.setAccessToken(accessToken);

        // Only update refresh token if we got a new one
        if (refreshToken != null && !refreshToken.isBlank()) {
            token.setRefreshToken(refreshToken);
            log.info("✅ New refresh token received");
        } else {
            log.warn("⚠️ No refresh token in response (using existing)");
        }

        token.setAccessTokenExpiresAt(expiresAt);
        token.setUpdatedAt(now);

        if (token.getCreatedAt() == null) {
            token.setCreatedAt(now);
        }

        tokenRepository.save(token);
        log.info("✅ Token saved to database for: {}", googleEmail);
        log.info("  Token ID: {}", token.getId());
        log.info("  Expires At: {}", expiresAt);
    }
}
