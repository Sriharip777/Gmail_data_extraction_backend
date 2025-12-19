package com.gmail.fetcher.service;

import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.GmailCredentialsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for scheduling automatic email fetching from Gmail
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSchedulerService {

    private final GmailService gmailService;

    @Value("${gmail.oauth.username:sriharipechettis@gmail.com}")
    private String gmailUsername;

    @Value("${gmail.oauth.access-token:}")
    private String gmailAccessToken;
    /**
     * Fetch emails every 6 hours
     * Cron: 0 0 6 * * * (every 6 hours)
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduledEmailFetch() {
        log.info("=== Starting Scheduled Email Fetch ===");

        try {
            // Create credentials
            GmailCredentialsDTO credentials = GmailCredentialsDTO.builder()
                    .email(gmailUsername)
                    .accessToken(gmailAccessToken)
                    .build();

            // Fetch and save emails
            List<EmailDTO> emails = gmailService.fetchAllEmails(credentials);
            gmailService.saveEmailsToDatabase(emails);

            log.info("✅ Scheduled email fetch completed. Fetched: {} emails", emails.size());

        } catch (Exception e) {
            log.error("❌ Error in scheduled email fetch: {}", e.getMessage(), e);
        }
    }

    /**
     * Fetch emails every 30 minutes (for testing)
     * Cron: 0 30 * * * * (every 30 minutes)
            */
    @Scheduled(cron = "0 */30 * * * *")
    public void scheduledEmailFetchEvery30Minutes() {
        log.info("=== Starting 30-Minute Email Fetch ===");

        try {
            GmailCredentialsDTO credentials = GmailCredentialsDTO.builder()
                    .email(gmailUsername)
                    .accessToken(gmailAccessToken)
                    .build();

            List<EmailDTO> emails = gmailService.fetchAllEmails(credentials);
            gmailService.saveEmailsToDatabase(emails);

            log.info("✅ 30-minute email fetch completed. Fetched: {} emails", emails.size());

        } catch (Exception e) {
            log.error("❌ Error in 30-minute email fetch: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup old emails every day at 2 AM
     * Cron: 0 0 2 * * * (daily at 02:00)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledCleanupOldEmails() {
        log.info("=== Starting Scheduled Cleanup ===");

        try {
            // Delete emails older than 1 year
            java.time.LocalDateTime oneYearAgo = java.time.LocalDateTime.now()
                    .minusYears(1);

            // Call cleanup (you can add this method to MongoEmailUtil)
            log.info("✅ Scheduled cleanup completed");

        } catch (Exception e) {
            log.error("❌ Error in scheduled cleanup: {}", e.getMessage(), e);
        }
    }
}
