package com.gmail.fetcher.controller;

import com.gmail.fetcher.config.GmailOAuthProperties;
import com.gmail.fetcher.config.GmailProperties;
import com.gmail.fetcher.entity.GmailToken;
import com.gmail.fetcher.repository.GmailTokenRepository;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Instant;
import java.util.Collections;

/**
 * Handles the OAuth2 callback from Google at /oauth2callback.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GmailCallbackController {

    private final GmailOAuthProperties oauthProperties;
    private final GmailProperties gmailProperties;
    private final GmailTokenRepository tokenRepository;

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    // Where to send the user after successful connection (React route)
    @Value("${frontend.redirect-url:http://localhost:5173/connected}")
    private String frontendRedirectUrl;

    /**
     * Step 2: Google redirects here with ?code=...
     */
    @GetMapping("/oauth2callback")
    public RedirectView handleGoogleCallback(@RequestParam("code") String code,
                                             HttpServletRequest request) throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(oauthProperties.getClientId())
                .setClientSecret(oauthProperties.getClientSecret());

        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JSON_FACTORY,
                clientSecrets,
                Collections.singletonList(gmailProperties.getScopes())
        ).setAccessType("offline").build();

        // 1) Exchange "code" for tokens
        TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(oauthProperties.getRedirectUri())
                .execute();

        String accessToken = tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken();
        Long expiresInSeconds = tokenResponse.getExpiresInSeconds();

        // 2) Get REAL app userId from your auth (no hard-coded "demo-user-id")
        //    Example if you use a session attribute or security context:
        //    String userId = (String) request.getSession().getAttribute("USER_ID");
        //    or from SecurityContextHolder...
        String userId = resolveCurrentUserId(request); // TODO: implement this in your app

        // 3) Get REAL Google email from ID token or People API
        String googleEmail = resolveGoogleEmail(tokenResponse); // TODO: implement helper

        Instant now = Instant.now();
        Instant expiresAt = expiresInSeconds != null ? now.plusSeconds(expiresInSeconds) : null;

        GmailToken token = tokenRepository.findByUserId(userId)
                .orElse(GmailToken.builder().userId(userId).build());

        token.setGoogleEmail(googleEmail);
        token.setAccessToken(accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            token.setRefreshToken(refreshToken);
        }
        token.setAccessTokenExpiresAt(expiresAt);
        token.setUpdatedAt(now);
        if (token.getCreatedAt() == null) {
            token.setCreatedAt(now);
        }

        tokenRepository.save(token);

        return new RedirectView(frontendRedirectUrl);
    }

    // Example stub – backend dev must connect to your auth system
    private String resolveCurrentUserId(HttpServletRequest request) {
        // e.g., from session, SecurityContext, or JWT
        // return (String) request.getSession().getAttribute("USER_ID");
        throw new IllegalStateException("resolveCurrentUserId not implemented");
    }

    // Example stub – backend dev can decode ID token or call People API
    private String resolveGoogleEmail(TokenResponse tokenResponse) {
        // For production, use GoogleIdToken and parse email from id_token,
        // or call https://people.googleapis.com/v1/people/me?personFields=emailAddresses
        // using the access token.
        throw new IllegalStateException("resolveGoogleEmail not implemented");
    }

}
