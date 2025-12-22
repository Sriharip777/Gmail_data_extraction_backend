package com.gmail.fetcher.controller;

import com.gmail.fetcher.repository.GmailTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Simple status endpoint so frontend can know if Gmail is connected.
 */
@RestController
@RequiredArgsConstructor
public class AuthStatusController {

    private final GmailTokenRepository tokenRepository;

    // TODO: replace hard-coded user id with real logged-in user
    private static final String DEMO_USER_ID = "demo-user-id";

    @GetMapping("/auth/gmail/status")
    public Map<String, Object> status() {
        boolean connected = tokenRepository.findByUserId(DEMO_USER_ID).isPresent();
        return Map.of(
                "connected", connected,
                "userId", DEMO_USER_ID
        );
    }
}
