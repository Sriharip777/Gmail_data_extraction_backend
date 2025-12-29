package com.gmail.fetcher.repository;

import com.gmail.fetcher.entity.GmailToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GmailTokenRepository extends MongoRepository<GmailToken, String> {

    Optional<GmailToken> findByUserId(String userId);

    List<GmailToken> findAllByUserId(String userId);

    void deleteByGoogleEmail(String googleEmail);
}
