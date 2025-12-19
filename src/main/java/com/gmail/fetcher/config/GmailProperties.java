package com.gmail.fetcher.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for Gmail API
 */
@Configuration
@ConfigurationProperties(prefix = "gmail.api")
@Data
@Validated
public class GmailProperties {

    @NotBlank(message = "Application name is required")
    private String applicationName = "Gmail Fetcher Application";

    @NotBlank(message = "Credentials file path is required")
    private String credentialsFilePath = "credentials.json";

    @NotBlank(message = "Tokens directory path is required")
    private String tokensDirectoryPath = "tokens";

    @NotBlank(message = "Scopes are required")
    private String scopes = "https://www.googleapis.com/auth/gmail.readonly";

    @Positive(message = "Max results must be positive")
    private Integer maxResults = 100;

    @NotBlank(message = "User ID is required")
    private String userId = "me";
}
