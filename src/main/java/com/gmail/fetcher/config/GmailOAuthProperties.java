package com.gmail.fetcher.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Gmail OAuth Properties
 */
@Configuration
@ConfigurationProperties(prefix = "gmail-oauth")
@Data
@Validated
@Slf4j
public class GmailOAuthProperties {

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Client secret is required")
    private String clientSecret;

    @NotBlank(message = "Redirect URI is required")
    private String redirectUri;

    @PostConstruct
    public void logConfig() {
        log.info("========================================");
        log.info("Gmail OAuth Configuration Loaded:");
        log.info("  Client ID: {}...", clientId != null ? clientId.substring(0, Math.min(20, clientId.length())) : "NULL");
        log.info("  Client Secret: {}", clientSecret != null ? "***SET***" : "NULL");
        log.info("  Redirect URI: {}", redirectUri);
        log.info("========================================");
    }
}
