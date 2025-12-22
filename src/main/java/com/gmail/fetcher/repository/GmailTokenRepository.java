package com.gmail.fetcher.repository;

import com.gmail.fetcher.entity.GmailToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repository for Gmail OAuth tokens.
 */
public interface GmailTokenRepository extends MongoRepository<GmailToken, String> {

    // Find token by your app user id
    Optional<GmailToken> findByUserId(String userId);

    // Optional: find by Google email
    Optional<GmailToken> findByGoogleEmail(String googleEmail);
}
