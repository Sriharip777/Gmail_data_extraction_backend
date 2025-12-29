package com.gmail.fetcher.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gmail API Properties
 */
@Configuration
@ConfigurationProperties(prefix = "gmail")
@Data
@Slf4j
public class GmailProperties {

    private String applicationName;
    private String credentialsFilePath;
    private String tokensDirectoryPath;
    private String scopes;

    /**
     * Get scopes as a list
     */
    public List<String> getScopesList() {
        if (scopes == null || scopes.isEmpty()) {
            return Collections.singletonList("https://www.googleapis.com/auth/gmail.readonly");
        }
        // Split by comma and trim spaces
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .collect(java.util.stream.Collectors.toList());
    }

    @PostConstruct
    public void logConfig() {
        log.info("========================================");
        log.info("Gmail Properties Loaded:");
        log.info("  Application Name: {}", applicationName);
        log.info("  Scopes: {}", scopes);
        log.info("  Credentials File: {}", credentialsFilePath);
        log.info("  Tokens Directory: {}", tokensDirectoryPath);
        log.info("========================================");
    }
}
