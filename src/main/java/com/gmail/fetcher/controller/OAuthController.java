package com.gmail.fetcher.controller;

import com.gmail.fetcher.util.GmailAuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final GmailAuthUtil gmailAuthUtil;

    /**
     * Get authorization URL
     * GET /oauth/authorize
     */
    @GetMapping("/authorize")
    public Map<String, String> getAuthUrl() {
        try {
            String authUrl = gmailAuthUtil.getAuthorizationUrl();

            Map<String, String> response = new HashMap<>();
            response.put("authorizationUrl", authUrl);
            response.put("message", "Open this URL in browser to authorize");

            return response;
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }
}
