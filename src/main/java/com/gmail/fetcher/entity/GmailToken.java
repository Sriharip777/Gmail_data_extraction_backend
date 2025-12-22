package com.gmail.fetcher.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Stores Gmail OAuth tokens for a user.
 */
@Document(collection = "gmail_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GmailToken {

    @Id
    private String id;

    // Your app's user identifier (can be email, userId, etc.)
    private String userId;

    // Google account email (the Gmail address)
    private String googleEmail;

    private String accessToken;
    private String refreshToken;

    // When the access token expires (epoch seconds or ISO)
    private Instant accessTokenExpiresAt;

    private Instant createdAt;
    private Instant updatedAt;
}
