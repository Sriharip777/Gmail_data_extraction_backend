package com.gmail.fetcher.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "gmail_tokens")
public class GmailToken {

    private String empId;


    @Id
    private String id;


    private String userId;                // owner in your app
    private String googleEmail;          // Gmail address

    private String accessToken;
    private String refreshToken;
    private Instant accessTokenExpiresAt;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastSyncedAt;        // optional, used by controllers
}
