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
 * Handles Google OAuth callback and redirects to frontend with tokens
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

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Handle Google OAuth2 callback
     * This endpoint receives the authorization code from Google
     * and exchanges it for access/refresh tokens
     */
    @GetMapping("/oauth2callback")
    public RedirectView handleGoogleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request) {

        log.info("========================================");
        log.info("📧 OAuth2 Callback Received");
        log.info("========================================");

        // ====================================
        // STEP 1: Handle OAuth errors
        // ====================================
        if (error != null) {
            log.error("❌ OAuth error from Google: {}", error);
            return new RedirectView(frontendUrl + "/gmail/inbox?error=oauth_" + error);
        }

        if (code == null || code.isBlank()) {
            log.error("❌ No authorization code received");
            return new RedirectView(frontendUrl + "/gmail/inbox?error=no_code");
        }

        log.info("✅ Authorization code received (length: {})", code.length());

        try {
            // ====================================
            // STEP 1: Get empId from session FIRST
            // ====================================
            HttpSession session = request.getSession();
            String empId = (String) session.getAttribute("empId");

            if (empId == null || empId.isBlank()) {
                log.error("❌ No empId in session! Cannot save token.");
                return new RedirectView(frontendUrl + "/gmail/inbox?error=no_empid");
            }

            log.info("✅ Found empId in session: {}", empId);

            // ====================================
            // STEP 2: Create HTTP transport
            // ====================================
            log.info("Creating HTTP transport...");
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // ====================================
            // STEP 3: Build Google client secrets
            // ====================================
            log.info("Building Google client secrets...");
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                    .setClientId(oauthProperties.getClientId())
                    .setClientSecret(oauthProperties.getClientSecret());

            GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

            // ====================================
            // STEP 4: Create authorization flow
            // ====================================
            log.info("Creating Google authorization flow...");
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    JSON_FACTORY,
                    clientSecrets,
                    gmailProperties.getScopesList()
            )
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            // ====================================
            // STEP 5: Exchange authorization code for tokens
            // ====================================
            log.info("Exchanging authorization code for access/refresh tokens...");
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(oauthProperties.getRedirectUri())
                    .execute();

            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();
            Long expiresInSeconds = tokenResponse.getExpiresInSeconds();

            log.info("✅ Tokens received successfully");
            log.info("  Access Token: {}", accessToken != null ? "Present (" + accessToken.length() + " chars)" : "❌ MISSING");
            log.info("  Refresh Token: {}", refreshToken != null ? "Present" : "⚠️ MISSING");
            log.info("  Expires In: {} seconds", expiresInSeconds);

            if (accessToken == null || accessToken.isBlank()) {
                log.error("❌ Access token is null or empty");
                return new RedirectView(frontendUrl + "/gmail/inbox?error=no_access_token");
            }

            // ====================================
            // STEP 6: Create credential and build Gmail service
            // ====================================
            log.info("Creating credential from token response...");
            Credential credential = flow.createAndStoreCredential(tokenResponse, USER_ID);

            log.info("Building Gmail service...");
            Gmail gmailService = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(gmailProperties.getApplicationName())
                    .build();

            // ====================================
            // STEP 7: Get user's email from Gmail API
            // ====================================
            log.info("Fetching user profile from Gmail API...");
            String googleEmail = gmailService.users()
                    .getProfile(USER_ID)
                    .execute()
                    .getEmailAddress();

            if (googleEmail == null || googleEmail.isBlank()) {
                log.error("❌ Gmail API returned null or empty email");
                return new RedirectView(frontendUrl + "/gmail/inbox?error=email_not_found");
            }

            log.info("✅ Successfully retrieved email from Gmail API: {}", googleEmail);

            // ====================================
            // STEP 8: Save token to database with empId
            // ====================================
            saveTokenToDatabase(googleEmail, accessToken, refreshToken, expiresInSeconds, empId);  // ✅ PASS empId

            // ====================================
            // STEP 9: Set active account in session
            // ====================================
            session.setAttribute("connectedEmail", googleEmail);
            log.info("✅ Set {} as active account in session", googleEmail);

            log.info("========================================");
            log.info("✅ OAuth flow completed successfully for empId: {}", empId);
            log.info("========================================");

            // ====================================
            // STEP 10: Redirect to frontend with tokens
            // ====================================
            String redirectUrl = String.format(
                    "%s/oauth2/callback?access_token=%s&email=%s",
                    frontendUrl,
                    accessToken,
                    googleEmail
            );

            // Add refresh token if present
            if (refreshToken != null && !refreshToken.isBlank()) {
                redirectUrl += "&refresh_token=" + refreshToken;
                log.info("✅ Including refresh token in redirect");
            } else {
                log.warn("⚠️ No refresh token to include in redirect");
            }

            log.info("✅ Redirecting to: {}", redirectUrl.replace(accessToken, "***TOKEN***"));
            return new RedirectView(redirectUrl);

        } catch (Exception e) {
            // ====================================
            // ERROR HANDLING
            // ====================================
            log.error("========================================");
            log.error("❌ Error in OAuth callback", e);
            log.error("========================================");

            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 100) {
                errorMessage = errorMessage.substring(0, 100);
            }

            return new RedirectView(frontendUrl + "/gmail/inbox?error=callback_failed&details=" +
                    java.net.URLEncoder.encode(errorMessage != null ? errorMessage : "Unknown error",
                            java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * Save Gmail token to database
     * Creates new token entry or updates existing one
     * ✅ FIXED: Now accepts empId parameter
     */
    private void saveTokenToDatabase(String googleEmail, String accessToken,
                                     String refreshToken, Long expiresInSeconds,
                                     String empId) {  // ✅ CORRECT parameter

        log.info("💾 Saving token for empId: {}, Gmail: {}", empId, googleEmail);

        Instant now = Instant.now();
        Instant expiresAt = (expiresInSeconds != null) ? now.plusSeconds(expiresInSeconds) : null;

        // ✅ Find existing token by empId, or create new
        GmailToken token = tokenRepository.findByEmpId(empId)
                .orElse(GmailToken.builder()
                        .empId(empId)          // ✅ Set empId
                        .userId(googleEmail)   // ✅ Set userId to googleEmail for backward compatibility
                        .createdAt(now)
                        .build());

        // Update token fields
        token.setGoogleEmail(googleEmail);
        token.setAccessToken(accessToken);
        token.setAccessTokenExpiresAt(expiresAt);
        token.setUpdatedAt(now);

        // Only update refresh token if we got a new one
        if (refreshToken != null && !refreshToken.isBlank()) {
            token.setRefreshToken(refreshToken);
            log.info("✅ New refresh token received and saved");
        } else {
            log.warn("⚠️ No refresh token in response (keeping existing if any)");
        }

        // Save to database
        GmailToken savedToken = tokenRepository.save(token);

        log.info("✅ Token saved to database successfully");
        log.info("  Token ID: {}", savedToken.getId());
        log.info("  empId: {}", savedToken.getEmpId());
        log.info("  User ID: {}", savedToken.getUserId());
        log.info("  Gmail: {}", savedToken.getGoogleEmail());
        log.info("  Expires At: {}", expiresAt);
        log.info("  Has Refresh Token: {}", savedToken.getRefreshToken() != null);
    }
}
