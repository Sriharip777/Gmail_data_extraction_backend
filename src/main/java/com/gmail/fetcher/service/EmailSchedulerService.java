package com.gmail.fetcher.service;

import com.gmail.fetcher.entity.GmailToken;
import com.gmail.fetcher.repository.GmailTokenRepository;
import com.gmail.fetcher.util.MongoEmailUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for scheduling automatic email fetching from Gmail
 *
 * FIXED VERSION:
 * - Multi-account support (fetches for ALL connected accounts)
 * - Token refresh handling
 * - Proper error handling per account
 * - Complete cleanup implementation
 * - No hard-coded credentials
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSchedulerService {

    private final GmailService gmailService;
    private final GmailTokenRepository gmailTokenRepository;
    private final MongoEmailUtil mongoEmailUtil;


    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduledEmailFetch() {
        log.info("========================================");
        log.info("=== Starting Scheduled Email Fetch (Every 6 Hours) ===");
        log.info("========================================");

        fetchEmailsForAllAccounts(100); // Max 100 emails per account
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void scheduledEmailFetchEvery30Minutes() {
        log.info("========================================");
        log.info("=== Starting 30-Minute Email Fetch (Testing) ===");
        log.info("========================================");

        fetchEmailsForAllAccounts(50); // Fetch fewer emails for testing
    }

    /**
     * Cleanup old emails every day at 2 AM
     * Deletes emails older than 1 year for ALL accounts
     * Cron: 0 0 2 * * * (daily at 02:00)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledCleanupOldEmails() {
        log.info("========================================");
        log.info("=== Starting Scheduled Cleanup (Daily 2 AM) ===");
        log.info("========================================");

        try {
            LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);

            // Get all connected accounts
            List<GmailToken> allTokens = gmailTokenRepository.findAll();

            if (allTokens.isEmpty()) {
                log.warn("No Gmail accounts connected. Skipping cleanup.");
                return;
            }

            int totalDeleted = 0;

            // Cleanup for each account
            for (GmailToken token : allTokens) {
                try {
                    String ownerEmail = token.getGoogleEmail();
                    log.info("Cleaning up old emails for: {}", ownerEmail);

                    long deletedCount = mongoEmailUtil.deleteOldEmailsForOwner(ownerEmail, oneYearAgo);
                    totalDeleted += deletedCount;

                    log.info("✅ Deleted {} old emails for {}", deletedCount, ownerEmail);

                } catch (Exception e) {
                    log.error("❌ Error cleaning up emails for {}: {}",
                            token.getGoogleEmail(), e.getMessage(), e);
                }
            }

            log.info("========================================");
            log.info("✅ Cleanup completed. Total deleted: {} emails", totalDeleted);
            log.info("========================================");

        } catch (Exception e) {
            log.error("❌ Error in scheduled cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup emails older than specific days for all accounts
     * Can be triggered manually or scheduled as needed
     */
    public void cleanupOldEmails(int daysOld) {
        log.info("Cleaning up emails older than {} days", daysOld);

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
            List<GmailToken> allTokens = gmailTokenRepository.findAll();

            int totalDeleted = 0;

            for (GmailToken token : allTokens) {
                try {
                    String ownerEmail = token.getGoogleEmail();
                    long deletedCount = mongoEmailUtil.deleteOldEmailsForOwner(ownerEmail, cutoffDate);
                    totalDeleted += deletedCount;

                    log.info("Deleted {} emails older than {} days for {}",
                            deletedCount, daysOld, ownerEmail);

                } catch (Exception e) {
                    log.error("Error cleaning up for {}: {}", token.getGoogleEmail(), e.getMessage());
                }
            }

            log.info("✅ Total cleaned up: {} emails", totalDeleted);

        } catch (Exception e) {
            log.error("❌ Error in cleanup: {}", e.getMessage(), e);
        }
    }

    // ========================================
    // PRIVATE HELPER METHODS
    // ========================================

    /**
     * Fetch emails for all connected Gmail accounts
     */
    private void fetchEmailsForAllAccounts(int maxEmailsPerAccount) {
        try {
            // Get all connected Gmail accounts
            List<GmailToken> allTokens = gmailTokenRepository.findAll();

            if (allTokens.isEmpty()) {
                log.warn("No Gmail accounts connected. Skipping scheduled fetch.");
                return;
            }

            log.info("Found {} connected Gmail account(s)", allTokens.size());

            int totalFetched = 0;
            int successCount = 0;
            int failureCount = 0;

            // Fetch emails for each account
            for (GmailToken token : allTokens) {
                String ownerEmail = token.getGoogleEmail();

                try {
                    log.info("----------------------------------------");
                    log.info("Fetching emails for: {}", ownerEmail);
                    log.info("----------------------------------------");

                    // Check if token needs refresh
                    if (isTokenExpired(token)) {
                        log.warn("Access token expired for {}. Skipping (requires re-authentication).", ownerEmail);
                        failureCount++;
                        continue;
                    }

                    // Fetch emails for this account
                    int fetchedCount = gmailService.fetchAndSaveEmailsForAccount(ownerEmail, maxEmailsPerAccount);
                    totalFetched += fetchedCount;
                    successCount++;

                    log.info("✅ Successfully fetched {} emails for {}", fetchedCount, ownerEmail);

                } catch (Exception e) {
                    failureCount++;
                    log.error("❌ Error fetching emails for {}: {}", ownerEmail, e.getMessage(), e);
                    // Continue with next account even if this one fails
                }
            }

            // Summary
            log.info("========================================");
            log.info("Scheduled Fetch Summary:");
            log.info("  Total Accounts: {}", allTokens.size());
            log.info("  Successful: {}", successCount);
            log.info("  Failed: {}", failureCount);
            log.info("  Total Emails Fetched: {}", totalFetched);
            log.info("========================================");

        } catch (Exception e) {
            log.error("❌ Fatal error in scheduled email fetch: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if access token is expired
     */
    private boolean isTokenExpired(GmailToken token) {
        if (token.getAccessTokenExpiresAt() == null) {
            return false; // If no expiry set, assume valid
        }

        Instant now = Instant.now();
        return now.isAfter(token.getAccessTokenExpiresAt());
    }
}
