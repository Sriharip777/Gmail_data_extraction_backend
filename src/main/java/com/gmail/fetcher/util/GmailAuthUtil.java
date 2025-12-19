package com.gmail.fetcher.util;

import com.gmail.fetcher.config.GmailOAuthProperties;
import com.gmail.fetcher.config.GmailProperties;
import com.gmail.fetcher.dto.GmailCredentialsDTO;
import com.gmail.fetcher.exception.GmailAuthException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
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
import java.util.Collections;
import java.util.List;

/**
 * Utility class for Gmail API Authentication
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GmailAuthUtil {

    private final GmailProperties gmailProperties;
    private final GmailOAuthProperties oauthProperties;

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);

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

