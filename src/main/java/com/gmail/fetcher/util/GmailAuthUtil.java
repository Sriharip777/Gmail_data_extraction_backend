package com.gmail.fetcher.util;

import com.gmail.fetcher.config.GmailOAuthProperties;
import com.gmail.fetcher.config.GmailProperties;
import com.gmail.fetcher.dto.GmailCredentialsDTO;
import com.gmail.fetcher.entity.GmailToken;
import com.gmail.fetcher.exception.GmailAuthException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Utility class for Gmail API Authentication
 * Supports multiple authentication methods:
 * - Credentials file (Desktop flow)
 * - Access token (API flow)
 * - OAuth2 (Web flow)
 * - Token refresh for multi-employee support
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GmailAuthUtil {

    private final GmailProperties gmailProperties;
    private final GmailOAuthProperties oauthProperties;

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);

    // ========================================
    // NEW METHODS FOR MULTI-EMPLOYEE SUPPORT
    // ========================================

    /**
     * ✅ Validate Gmail token and check if refresh is possible
     * @param token The GmailToken entity to validate
     * @return true if token is valid or can be refreshed, false otherwise
     */
    public boolean validateGmailToken(GmailToken token) {
        if (token == null) {
            log.error("Token is null");
            return false;
        }

        if (token.getAccessToken() == null || token.getAccessToken().isEmpty()) {
            log.error("Access token is null or empty for empId: {}", token.getEmpId());
            return false;
        }

        if (token.getAccessTokenExpiresAt() == null) {
            log.warn("Token expiry time is null for empId: {} - assuming valid if refresh token exists",
                    token.getEmpId());
            return token.getRefreshToken() != null && !token.getRefreshToken().isEmpty();
        }

        // Check if token is expired
        if (Instant.now().isAfter(token.getAccessTokenExpiresAt())) {
            log.info("Token expired for empId: {} - checking for refresh token", token.getEmpId());
            // Valid if we can refresh it
            boolean canRefresh = token.getRefreshToken() != null && !token.getRefreshToken().isEmpty();
            if (!canRefresh) {
                log.error("Token expired and no refresh token available for empId: {}", token.getEmpId());
            }
            return canRefresh;
        }

        log.debug("Token is valid for empId: {}", token.getEmpId());
        return true;
    }

    /**
     * ✅ Create Gmail service with automatic token refresh support
     * This method checks token expiry and refreshes if needed
     * @param token The GmailToken entity containing access and refresh tokens
     * @return Gmail service instance
     * @throws IOException if token refresh fails or service creation fails
     */
    public Gmail createGmailServiceWithRefreshToken(GmailToken token) throws IOException {
        try {
            // Check if token needs refresh (refresh 5 minutes before expiry)
            if (token.getAccessTokenExpiresAt() != null &&
                    Instant.now().isAfter(token.getAccessTokenExpiresAt().minusSeconds(300))) {
                log.info("Token expired or expiring soon for empId: {}, refreshing...", token.getEmpId());
                refreshAccessToken(token);
            }

            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // Create credential with access token and refresh token
            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(httpTransport)
                    .setJsonFactory(JSON_FACTORY)
                    .setClientSecrets(oauthProperties.getClientId(), oauthProperties.getClientSecret())
                    .build()
                    .setAccessToken(token.getAccessToken())
                    .setRefreshToken(token.getRefreshToken());

            // Set expiration time if available
            if (token.getAccessTokenExpiresAt() != null) {
                credential.setExpirationTimeMilliseconds(token.getAccessTokenExpiresAt().toEpochMilli());
            }

            // Build Gmail service
            Gmail service = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(gmailProperties.getApplicationName())
                    .build();

            log.info("✅ Successfully created Gmail service for empId: {}", token.getEmpId());
            return service;

        } catch (GeneralSecurityException e) {
            log.error("❌ Security error creating Gmail service for empId {}: {}",
                    token.getEmpId(), e.getMessage());
            throw new IOException("Failed to create Gmail service due to security error", e);
        }
    }

    /**
     * ✅ Refresh access token using refresh token
     * Updates the token object with new access token and expiry time
     * @param token The GmailToken entity to refresh (will be updated in-place)
     * @throws IOException if refresh fails
     */
    public void refreshAccessToken(GmailToken token) throws IOException {
        if (token.getRefreshToken() == null || token.getRefreshToken().isEmpty()) {
            log.error("❌ No refresh token available for empId: {}", token.getEmpId());
            throw new IOException("No refresh token available - user must re-authenticate");
        }

        try {
            log.info("🔄 Refreshing access token for empId: {}", token.getEmpId());

            NetHttpTransport httpTransport = new NetHttpTransport();

            // Use Google's refresh token request
            TokenResponse response = new GoogleRefreshTokenRequest(
                    httpTransport,
                    JSON_FACTORY,
                    token.getRefreshToken(),
                    oauthProperties.getClientId(),
                    oauthProperties.getClientSecret())
                    .execute();

            // Update token with new access token
            token.setAccessToken(response.getAccessToken());

            // Update expiry time if provided
            if (response.getExpiresInSeconds() != null) {
                Instant newExpiry = Instant.now().plusSeconds(response.getExpiresInSeconds());
                token.setAccessTokenExpiresAt(newExpiry);
                log.info("✅ Token refreshed successfully for empId: {}, expires at: {}",
                        token.getEmpId(), newExpiry);
            } else {
                log.warn("⚠️ Token refreshed but no expiry time provided for empId: {}", token.getEmpId());
            }

            // Note: You should save the updated token to database after calling this method

        } catch (Exception e) {
            log.error("❌ Failed to refresh token for empId {}: {}", token.getEmpId(), e.getMessage(), e);
            throw new IOException("Token refresh failed - user must re-authenticate: " + e.getMessage(), e);
        }
    }

    // ========================================
    // EXISTING AUTHENTICATION METHODS
    // ========================================

    /**
     * Method 1: Authenticate using credentials.json file (Desktop App Flow)
     * This will open a browser for user authorization on first run
     * Subsequent runs will use stored credentials from tokens directory
     */
    public Gmail getGmailServiceWithCredentialsFile() throws Exception {
        log.info("Authenticating with credentials file: {}", gmailProperties.getCredentialsFilePath());

        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // Load credentials from classpath
            InputStream in = new ClassPathResource(gmailProperties.getCredentialsFilePath()).getInputStream();
            if (in == null) {
                throw new FileNotFoundException("Credential file not found: " + gmailProperties.getCredentialsFilePath());
            }

            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            // Build flow and trigger user authorization request
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(gmailProperties.getTokensDirectoryPath())))
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                    .setPort(8888)
                    .build();

            Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

            log.info("Authentication successful! Credentials stored in: {}", gmailProperties.getTokensDirectoryPath());

            return new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(gmailProperties.getApplicationName())
                    .build();

        } catch (Exception e) {
            log.error("Error during credentials file authentication: {}", e.getMessage(), e);
            throw new GmailAuthException("Failed to authenticate with credentials file", e);
        }
    }

    /**
     * Method 2: Authenticate using access token (API Flow)
     * Use this when you already have an access token from OAuth flow
     */
    public Gmail getGmailService(GmailCredentialsDTO credentials) throws Exception {
        log.info("Authenticating with access token for user: {}", credentials.getEmail());

        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // Create credentials from access token
            AccessToken accessToken = new AccessToken(credentials.getAccessToken(), null);
            GoogleCredentials googleCredentials = GoogleCredentials.create(accessToken);

            Gmail service = new Gmail.Builder(httpTransport, JSON_FACTORY,
                    new HttpCredentialsAdapter(googleCredentials))
                    .setApplicationName(gmailProperties.getApplicationName())
                    .build();

            log.info("Successfully created Gmail service with access token");
            return service;

        } catch (Exception e) {
            log.error("Error creating Gmail service with access token: {}", e.getMessage(), e);
            throw new GmailAuthException("Failed to authenticate with access token", e);
        }
    }

    /**
     * Method 3: Authenticate using Client ID and Secret (OAuth2 Flow)
     * Use this for web applications with programmatic OAuth
     */
    public Gmail getGmailServiceWithOAuth() throws Exception {
        log.info("Authenticating with OAuth credentials...");

        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // Create client secrets programmatically
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                    .setClientId(oauthProperties.getClientId())
                    .setClientSecret(oauthProperties.getClientSecret());

            GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

            // Build flow
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(gmailProperties.getTokensDirectoryPath())))
                    .setAccessType("offline")
                    .build();

            LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                    .setPort(8080)
                    .build();

            Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

            log.info("OAuth authentication successful!");

            return new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(gmailProperties.getApplicationName())
                    .build();

        } catch (Exception e) {
            log.error("Error during OAuth authentication: {}", e.getMessage(), e);
            throw new GmailAuthException("Failed to authenticate with OAuth", e);
        }
    }

    /**
     * Get the authorization code flow
     * Used by OAuthController to exchange authorization code for tokens
     */
    public GoogleAuthorizationCodeFlow getAuthorizationCodeFlow() throws IOException {
        log.info("Creating GoogleAuthorizationCodeFlow...");

        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                    .setClientId(oauthProperties.getClientId())
                    .setClientSecret(oauthProperties.getClientSecret());

            GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

            return new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

        } catch (Exception e) {
            log.error("Error creating authorization flow: {}", e.getMessage(), e);
            throw new GmailAuthException("Failed to create authorization flow", e);
        }
    }

    /**
     * Get user email from credential
     * Extracts the user's Gmail address from their credential
     */
    public String getUserEmailFromCredential(Credential credential) {
        log.info("Retrieving user email from credential...");

        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            Gmail service = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(gmailProperties.getApplicationName())
                    .build();

            String email = service.users().getProfile("me").execute().getEmailAddress();
            log.info("User email retrieved: {}", email);
            return email;

        } catch (Exception e) {
            log.error("Error retrieving user email: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get authorization URL for web-based OAuth flow
     * Returns the URL where user should be redirected for authorization
     */
    public String getAuthorizationUrl() throws IOException {
        log.info("Generating authorization URL...");

        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                    .setClientId(oauthProperties.getClientId())
                    .setClientSecret(oauthProperties.getClientSecret());

            GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            String authUrl = flow.newAuthorizationUrl()
                    .setRedirectUri(oauthProperties.getRedirectUri())
                    .build();

            log.info("Authorization URL generated successfully");
            return authUrl;

        } catch (Exception e) {
            log.error("Error generating authorization URL: {}", e.getMessage(), e);
            throw new GmailAuthException("Failed to generate authorization URL", e);
        }
    }

    /**
     * Validate if credentials are properly configured
     */
    public boolean validateCredentials(GmailCredentialsDTO credentials) {
        if (credentials == null) {
            log.error("Credentials object is null");
            return false;
        }

        if (credentials.getAccessToken() == null || credentials.getAccessToken().isEmpty()) {
            log.error("Access token is missing");
            return false;
        }

        if (credentials.getEmail() == null || credentials.getEmail().isEmpty()) {
            log.error("Email is missing");
            return false;
        }

        log.debug("Credentials validation successful");
        return true;
    }
}
