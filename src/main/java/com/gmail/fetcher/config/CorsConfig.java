package com.gmail.fetcher.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * CORS Configuration for cross-origin requests
 * Handles session cookies and credentials across frontend-backend communication
 */
@Configuration
@Slf4j
public class CorsConfig {

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:4200,http://localhost:5173}")
    private String[] allowedOrigins;

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("🌐 CORS Configuration Initialized");
        log.info("========================================");
        log.info("  Frontend URL: {}", frontendUrl);
        log.info("  Allowed Origins: {}", Arrays.toString(allowedOrigins));
        log.info("  Credentials Allowed: true");
        log.info("  Session Cookie Support: enabled");
        log.info("========================================");
    }

    @Bean
    public CorsFilter corsFilter() {
        log.info("Creating CORS Filter with enhanced session support...");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // ========================================
        // ENABLE CREDENTIALS (Required for sessions/cookies)
        // ========================================
        config.setAllowCredentials(true);
        log.debug("✅ Credentials allowed: true");

        // ========================================
        // ALLOWED ORIGINS
        // ========================================
        // Combine configured origins with frontend URL
        List<String> origins = new java.util.ArrayList<>(Arrays.asList(allowedOrigins));
        if (!origins.contains(frontendUrl)) {
            origins.add(frontendUrl);
        }
        // Always allow localhost:8080 for same-origin requests
        if (!origins.contains("http://localhost:8080")) {
            origins.add("http://localhost:8080");
        }

        config.setAllowedOrigins(origins);
        log.debug("✅ Allowed origins: {}", origins);

        // ========================================
        // ALLOWED HEADERS
        // ========================================
        config.setAllowedHeaders(Arrays.asList(
                "*",  // Allow all headers
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "Cookie",
                "Set-Cookie"
        ));
        log.debug("✅ All headers allowed");

        // ========================================
        // ALLOWED METHODS
        // ========================================
        config.setAllowedMethods(Arrays.asList(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS",
                "PATCH",
                "HEAD"
        ));
        log.debug("✅ Allowed methods: GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");

        // ========================================
        // EXPOSED HEADERS (For session cookies)
        // ========================================
        config.setExposedHeaders(Arrays.asList(
                "Set-Cookie",
                "Authorization",
                "Content-Type",
                "Content-Length",
                "X-Total-Count",
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials"
        ));
        log.debug("✅ Exposed headers configured for session support");

        // ========================================
        // PREFLIGHT CACHE
        // ========================================
        // Cache preflight requests for 1 hour (3600 seconds)
        config.setMaxAge(3600L);
        log.debug("✅ Preflight cache: 3600 seconds (1 hour)");

        // ========================================
        // APPLY CONFIGURATION
        // ========================================
        source.registerCorsConfiguration("/**", config);
        log.info("✅ CORS Filter created successfully");
        log.info("   Applied to: /**");
        log.info("   Session cookie support: ENABLED");

        return new CorsFilter(source);
    }
}
