package com.gmail.fetcher.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configuration properties for Gmail OAuth2
 */
@Configuration
@ConfigurationProperties(prefix = "gmail.oauth")
@Data
@Validated
public class GmailOAuthProperties {

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Client secret is required")
    private String clientSecret;

    @NotBlank(message = "Redirect URI is required")
    private String redirectUri = "http://localhost:8080/oauth2callback";
}
