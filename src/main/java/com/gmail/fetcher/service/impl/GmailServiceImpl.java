package com.gmail.fetcher.service.impl;

import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.EmailFilterDTO;
import com.gmail.fetcher.dto.GmailCredentialsDTO;
import com.gmail.fetcher.entity.EmailMessage;
import com.gmail.fetcher.entity.GmailToken;
import com.gmail.fetcher.exception.EmailFetchException;
import com.gmail.fetcher.repository.EmailMessageRepository;
import com.gmail.fetcher.repository.GmailTokenRepository;
import com.gmail.fetcher.service.EmailFilterService;
import com.gmail.fetcher.service.GmailService;
import com.gmail.fetcher.util.EmailParserUtil;
import com.gmail.fetcher.util.GmailAuthUtil;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Complete Implementation of GmailService with Multi-Account Support
 *
 * Features:
 * - Multi-account Gmail support with ownerEmail tracking
 * - Fetch emails with pagination
 * - Filter emails by various criteria
 * - Save/update emails in MongoDB
 * - Search emails in database
 * - Account-specific operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GmailServiceImpl implements GmailService {

    private static final String USER_ID_ME = "me";

    private final EmailMessageRepository emailRepository;
    private final GmailTokenRepository gmailTokenRepository;
    private final EmailFilterService filterService;
    private final GmailAuthUtil gmailAuthUtil;
    private final EmailParserUtil emailParserUtil;
    private final MongoTemplate mongoTemplate;

    // ========================================
    // NEW MULTI-ACCOUNT METHODS
    // ========================================

    /**
     * Fetch and save emails for specific account with limit
     * This is the main method for multi-account email fetching
     */
    @Override
    @Transactional
    public int fetchAndSaveEmailsForAccount(String ownerEmail, int maxEmails) {
        log.info("========================================");
        log.info("Fetching max {} emails for account: {}", maxEmails, ownerEmail);
        log.info("========================================");

        try {
            // Get token for this account
            GmailToken token = gmailTokenRepository.findByUserId(ownerEmail)
                    .orElseThrow(() -> new EmailFetchException("Token not found for: " + ownerEmail));

            // Build credentials
            GmailCredentialsDTO credentials = GmailCredentialsDTO.builder()
                    .email(token.getGoogleEmail())
                    .accessToken(token.getAccessToken())
                    .refreshToken(token.getRefreshToken())
                    .build();

            // Validate credentials
            if (!gmailAuthUtil.validateCredentials(credentials)) {
                throw new EmailFetchException("Invalid Gmail credentials for: " + ownerEmail);
            }

            Gmail service = gmailAuthUtil.getGmailService(credentials);

            // Fetch emails with limit
            List<EmailDTO> emails = new ArrayList<>();

            ListMessagesResponse response = service.users().messages()
                    .list(USER_ID_ME)
                    .setMaxResults((long) maxEmails)
                    .execute();

            List<Message> messageHeaders = response.getMessages();

            if (messageHeaders != null && !messageHeaders.isEmpty()) {
                log.info("Found {} message(s) to fetch", messageHeaders.size());

                for (Message header : messageHeaders) {
                    try {
                        Message full = service.users().messages()
                                .get(USER_ID_ME, header.getId())
                                .setFormat("full")
                                .execute();

                        EmailDTO dto = emailParserUtil.parseMessage(full);
                        emails.add(dto);

                    } catch (Exception ex) {
                        log.error("Error parsing message {}: {}", header.getId(), ex.getMessage());
                    }
                }
            } else {
                log.info("No messages found for account: {}", ownerEmail);
            }

            // Save to database with ownerEmail
            saveEmailsToDatabase(emails, ownerEmail);

            // Update last sync time
            token.setLastSyncedAt(Instant.now());
            gmailTokenRepository.save(token);

            log.info("✅ Successfully fetched and saved {} emails for {}", emails.size(), ownerEmail);
            return emails.size();

        } catch (Exception e) {
            log.error("❌ Error fetching emails for {}: {}", ownerEmail, e.getMessage(), e);
            throw new EmailFetchException("Failed to fetch emails for account", e);
        }
    }

    /**
     * Get statistics for specific account
     */
    @Override
    public Map<String, Object> getStatsForAccount(String ownerEmail) {
        log.info("Getting statistics for account: {}", ownerEmail);

        Map<String, Object> stats = new HashMap<>();

        // Get counts using correct repository methods
        long totalEmails = emailRepository.countByOwnerEmail(ownerEmail);
        long unreadEmails = emailRepository.countByOwnerEmailAndIsRead(ownerEmail, false);
        long starredEmails = emailRepository.countByOwnerEmailAndIsStarred(ownerEmail, true);

        stats.put("totalEmails", totalEmails);
        stats.put("unreadEmails", unreadEmails);
        stats.put("starredEmails", starredEmails);
        stats.put("readEmails", totalEmails - unreadEmails);

        // Get recent sync time
        GmailToken token = gmailTokenRepository.findByUserId(ownerEmail).orElse(null);
        if (token != null) {
            stats.put("lastSynced", token.getLastSyncedAt());
            stats.put("tokenCreated", token.getCreatedAt());
        } else {
            stats.put("lastSynced", null);
            stats.put("tokenCreated", null);
        }

        log.info("Stats for {}: Total={}, Unread={}, Starred={}",
                ownerEmail, totalEmails, unreadEmails, starredEmails);

        return stats;
    }


    /**
     * Search emails for specific account
     */
    @Override
    public List<EmailDTO> searchEmailsForAccount(String ownerEmail, EmailFilterDTO filter) {
        log.info("Searching emails for account: {} with filter: {}", ownerEmail, filter);

        try {
            Query query = new Query();

            // MUST match owner
            query.addCriteria(Criteria.where("ownerEmail").is(ownerEmail));

            // Apply additional filters
            if (filter.getFromEmail() != null && !filter.getFromEmail().isEmpty()) {
                query.addCriteria(Criteria.where("fromEmail").regex(filter.getFromEmail(), "i"));
            }
            if (filter.getToEmail() != null && !filter.getToEmail().isEmpty()) {
                query.addCriteria(Criteria.where("toEmail").regex(filter.getToEmail(), "i"));
            }
            if (filter.getSubject() != null && !filter.getSubject().isEmpty()) {
                query.addCriteria(Criteria.where("subject").regex(filter.getSubject(), "i"));
            }
            if (filter.getStartDate() != null && filter.getEndDate() != null) {
                query.addCriteria(Criteria.where("receivedDate")
                        .gte(filter.getStartDate())
                        .lte(filter.getEndDate()));
            }
            if (filter.getIsRead() != null) {
                query.addCriteria(Criteria.where("isRead").is(filter.getIsRead()));
            }
            if (filter.getIsStarred() != null) {
                query.addCriteria(Criteria.where("isStarred").is(filter.getIsStarred()));
            }
            if (filter.getLabels() != null && !filter.getLabels().isEmpty()) {
                query.addCriteria(Criteria.where("labels").in(filter.getLabels()));
            }

            List<EmailMessage> messages = mongoTemplate.find(query, EmailMessage.class);
            log.info("✅ Found {} emails for account {}", messages.size(), ownerEmail);

            return messages.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error searching emails for account {}: {}", ownerEmail, e.getMessage(), e);
            throw new EmailFetchException("Failed to search emails for account", e);
        }
    }

    // ========================================
    // ORIGINAL METHODS (UPDATED FOR MULTI-ACCOUNT)
    // ========================================

    /**
     * Fetch all emails from Gmail
     * Note: This fetches ALL emails without limit (use with caution)
     */
    @Override
    public List<EmailDTO> fetchAllEmails(GmailCredentialsDTO credentials) {
        log.info("========================================");
        log.info("Fetching ALL emails for user: {}", credentials.getEmail());
        log.info("========================================");

        try {
            if (!gmailAuthUtil.validateCredentials(credentials)) {
                throw new EmailFetchException("Invalid Gmail credentials provided");
            }

            Gmail service = gmailAuthUtil.getGmailService(credentials);

            List<EmailDTO> emails = new ArrayList<>();
            String pageToken = null;
            long pageCount = 0;

            do {
                ListMessagesResponse response = service.users().messages()
                        .list(USER_ID_ME)
                        .setMaxResults(100L)
                        .setPageToken(pageToken)
                        .execute();

                pageCount++;
                log.info("Processing page {} of messages", pageCount);

                List<Message> messageHeaders = response.getMessages();
                if (messageHeaders != null && !messageHeaders.isEmpty()) {
                    log.info("Found {} message(s) in page {}", messageHeaders.size(), pageCount);

                    for (Message header : messageHeaders) {
                        try {
                            Message full = service.users().messages()
                                    .get(USER_ID_ME, header.getId())
                                    .setFormat("full")
                                    .execute();

                            EmailDTO dto = emailParserUtil.parseMessage(full);
                            emails.add(dto);
                            log.debug("Parsed email: {} from {}", dto.getSubject(), dto.getFromEmail());

                        } catch (Exception ex) {
                            log.error("Error parsing message {}: {}", header.getId(), ex.getMessage(), ex);
                        }
                    }
                } else {
                    log.info("No messages found in this page");
                }

                pageToken = response.getNextPageToken();
            } while (pageToken != null);

            log.info("✅ Successfully fetched {} emails from Gmail (all pages)", emails.size());
            return emails;

        } catch (Exception e) {
            log.error("❌ Error fetching emails from Gmail: {}", e.getMessage(), e);
            throw new EmailFetchException("Failed to fetch emails from Gmail", e);
        }
    }

    /**
     * Fetch emails with filter
     */
    @Override
    public List<EmailDTO> fetchEmailsWithFilter(GmailCredentialsDTO credentials, EmailFilterDTO filter) {
        log.info("========================================");
        log.info("Fetching FILTERED emails for user: {}", credentials.getEmail());
        log.info("Filter: {}", filter);
        log.info("========================================");

        try {
            if (!gmailAuthUtil.validateCredentials(credentials)) {
                throw new EmailFetchException("Invalid Gmail credentials provided");
            }

            if (!filterService.validateFilter(filter)) {
                throw new EmailFetchException("Invalid filter criteria provided");
            }

            Gmail service = gmailAuthUtil.getGmailService(credentials);

            String queryStr = filterService.buildGmailQuery(filter);
            log.debug("Built Gmail query: {}", queryStr);

            List<EmailDTO> emails = new ArrayList<>();
            String pageToken = null;
            long pageCount = 0;
            long maxResultsPerPage = (filter.getMaxResults() != null
                    ? filter.getMaxResults().longValue()
                    : 100L);

            do {
                ListMessagesResponse response = service.users().messages()
                        .list(USER_ID_ME)
                        .setQ(queryStr)
                        .setMaxResults(maxResultsPerPage)
                        .setPageToken(pageToken)
                        .execute();

                pageCount++;
                log.info("Processing page {} of filtered messages", pageCount);

                List<Message> messageHeaders = response.getMessages();
                if (messageHeaders != null && !messageHeaders.isEmpty()) {
                    log.info("Found {} filtered message(s) in page {}", messageHeaders.size(), pageCount);

                    for (Message header : messageHeaders) {
                        try {
                            Message full = service.users().messages()
                                    .get(USER_ID_ME, header.getId())
                                    .setFormat("full")
                                    .execute();

                            EmailDTO dto = emailParserUtil.parseMessage(full);
                            emails.add(dto);
                            log.debug("Parsed filtered email: {}", dto.getSubject());

                        } catch (Exception ex) {
                            log.error("Error parsing message {}: {}", header.getId(), ex.getMessage(), ex);
                        }
                    }
                } else {
                    log.info("No messages found matching filter criteria in this page");
                }

                pageToken = response.getNextPageToken();
            } while (pageToken != null);

            log.info("✅ Successfully fetched {} filtered emails (all pages)", emails.size());
            return emails;

        } catch (Exception e) {
            log.error("❌ Error fetching filtered emails: {}", e.getMessage(), e);
            throw new EmailFetchException("Failed to fetch filtered emails", e);
        }
    }

    /**
     * Fetch single email by ID
     */
    @Override
    public EmailDTO fetchEmailById(GmailCredentialsDTO credentials, String messageId) {
        log.info("Fetching single email with ID: {}", messageId);

        try {
            if (!gmailAuthUtil.validateCredentials(credentials)) {
                throw new EmailFetchException("Invalid Gmail credentials provided");
            }

            Gmail service = gmailAuthUtil.getGmailService(credentials);

            Message message = service.users().messages()
                    .get(USER_ID_ME, messageId)
                    .setFormat("full")
                    .execute();

            if (message == null) {
                throw new EmailFetchException("Message not found: " + messageId);
            }

            EmailDTO dto = emailParserUtil.parseMessage(message);
            log.info("✅ Successfully fetched email: {}", dto.getSubject());
            return dto;

        } catch (Exception e) {
            log.error("❌ Error fetching email by ID {}: {}", messageId, e.getMessage(), e);
            throw new EmailFetchException("Failed to fetch email by ID: " + messageId, e);
        }
    }

    /**
     * Save emails to database with ownerEmail tracking
     * UPDATED: Now requires ownerEmail parameter
     */
    @Override
    @Transactional
    public void saveEmailsToDatabase(List<EmailDTO> emails, String ownerEmail) {
        log.info("========================================");
        log.info("Saving {} emails for account: {}", emails.size(), ownerEmail);
        log.info("========================================");

        if (emails == null || emails.isEmpty()) {
            log.warn("No emails to save");
            return;
        }

        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        long startTime = System.currentTimeMillis();

        for (EmailDTO dto : emails) {
            if (dto == null || dto.getMessageId() == null) {
                log.warn("Skipping email with null DTO or messageId");
                skippedCount++;
                continue;
            }

            try {
                // Check if email exists for THIS owner
                Optional<EmailMessage> existing = emailRepository
                        .findByOwnerEmailAndMessageId(ownerEmail, dto.getMessageId());

                if (existing.isPresent()) {
                    EmailMessage entity = existing.get();
                    updateEntityFromDTO(entity, dto);
                    emailRepository.save(entity);
                    updatedCount++;
                } else {
                    EmailMessage entity = convertToEntity(dto, ownerEmail);
                    emailRepository.save(entity);
                    savedCount++;
                }

            } catch (Exception e) {
                log.error("❌ Error saving email {}: {}", dto.getMessageId(), e.getMessage(), e);
                skippedCount++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        log.info("========================================");
        log.info("Database Operation Summary for {}:", ownerEmail);
        log.info("  Saved:   {} new emails", savedCount);
        log.info("  Updated: {} existing emails", updatedCount);
        log.info("  Skipped: {} emails (errors)", skippedCount);
        log.info("  Duration: {} ms", duration);
        log.info("  Total for this account: {}", emailRepository.countByOwnerEmail(ownerEmail));
        log.info("========================================");
    }

    /**
     * Search emails in database (all accounts - legacy method)
     */
    @Override
    public List<EmailDTO> searchEmailsInDatabase(EmailFilterDTO filter) {
        log.info("Searching emails in database (all accounts)");
        log.debug("Search filter: {}", filter);

        try {
            Query query = new Query();

            if (filter.getFromEmail() != null && !filter.getFromEmail().isEmpty()) {
                query.addCriteria(Criteria.where("fromEmail").regex(filter.getFromEmail(), "i"));
            }
            if (filter.getToEmail() != null && !filter.getToEmail().isEmpty()) {
                query.addCriteria(Criteria.where("toEmail").regex(filter.getToEmail(), "i"));
            }
            if (filter.getSubject() != null && !filter.getSubject().isEmpty()) {
                query.addCriteria(Criteria.where("subject").regex(filter.getSubject(), "i"));
            }
            if (filter.getStartDate() != null && filter.getEndDate() != null) {
                query.addCriteria(Criteria.where("receivedDate")
                        .gte(filter.getStartDate())
                        .lte(filter.getEndDate()));
            }
            if (filter.getIsRead() != null) {
                query.addCriteria(Criteria.where("isRead").is(filter.getIsRead()));
            }
            if (filter.getIsStarred() != null) {
                query.addCriteria(Criteria.where("isStarred").is(filter.getIsStarred()));
            }
            if (filter.getLabels() != null && !filter.getLabels().isEmpty()) {
                query.addCriteria(Criteria.where("labels").in(filter.getLabels()));
            }

            List<EmailMessage> messages = mongoTemplate.find(query, EmailMessage.class);
            log.info("✅ Found {} emails in database", messages.size());

            return messages.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error searching emails in database: {}", e.getMessage(), e);
            throw new EmailFetchException("Failed to search emails in database", e);
        }
    }

    /**
     * Get Gmail service with credentials file
     */
    @Override
    public Gmail getGmailServiceWithFile() throws Exception {
        log.info("Getting Gmail service with credentials file");
        return gmailAuthUtil.getGmailServiceWithCredentialsFile();
    }

    /**
     * Sync emails from Gmail to database
     */
    @Override
    @Transactional
    public void syncEmailsFromGmail(GmailCredentialsDTO credentials) {
        log.info("========================================");
        log.info("Starting FULL EMAIL SYNC from Gmail");
        log.info("========================================");

        try {
            List<EmailDTO> emails = fetchAllEmails(credentials);
            saveEmailsToDatabase(emails, credentials.getEmail());
            log.info("✅ Email sync completed successfully! Total synced: {} emails", emails.size());

        } catch (Exception e) {
            log.error("❌ Error during email sync: {}", e.getMessage(), e);
            throw new EmailFetchException("Failed to sync emails from Gmail", e);
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Convert DTO to Entity with ownerEmail
     * UPDATED: Added ownerEmail parameter
     */
    private EmailMessage convertToEntity(EmailDTO dto, String ownerEmail) {
        LocalDateTime now = LocalDateTime.now();
        return EmailMessage.builder()
                .ownerEmail(ownerEmail)  // NEW: Set owner
                .messageId(dto.getMessageId())
                .subject(dto.getSubject())
                .fromEmail(dto.getFromEmail())
                .toEmail(dto.getToEmail())
                .ccEmail(dto.getCcEmail())
                .bccEmail(dto.getBccEmail())
                .bodyText(dto.getBodyText())
                .bodyHtml(dto.getBodyHtml())
                .receivedDate(dto.getReceivedDate())
                .isRead(dto.getIsRead())
                .isStarred(dto.getIsStarred())
                .hasAttachments(dto.getHasAttachments())
                .labels(dto.getLabels())
                .threadId(dto.getThreadId())
                .snippet(dto.getSnippet())
                .sizeEstimate(dto.getSizeEstimate())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Update entity from DTO
     */
    private void updateEntityFromDTO(EmailMessage entity, EmailDTO dto) {
        entity.setSubject(dto.getSubject());
        entity.setFromEmail(dto.getFromEmail());
        entity.setToEmail(dto.getToEmail());
        entity.setCcEmail(dto.getCcEmail());
        entity.setBccEmail(dto.getBccEmail());
        entity.setBodyText(dto.getBodyText());
        entity.setBodyHtml(dto.getBodyHtml());
        entity.setIsRead(dto.getIsRead());
        entity.setIsStarred(dto.getIsStarred());
        entity.setHasAttachments(dto.getHasAttachments());
        entity.setLabels(dto.getLabels());
        entity.setThreadId(dto.getThreadId());
        entity.setSnippet(dto.getSnippet());
        entity.setSizeEstimate(dto.getSizeEstimate());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Convert entity to DTO
     */
    private EmailDTO convertToDTO(EmailMessage entity) {
        return EmailDTO.builder()
                .messageId(entity.getMessageId())
                .subject(entity.getSubject())
                .fromEmail(entity.getFromEmail())
                .toEmail(entity.getToEmail())
                .ccEmail(entity.getCcEmail())
                .bccEmail(entity.getBccEmail())
                .bodyText(entity.getBodyText())
                .bodyHtml(entity.getBodyHtml())
                .receivedDate(entity.getReceivedDate())
                .isRead(entity.getIsRead())
                .isStarred(entity.getIsStarred())
                .hasAttachments(entity.getHasAttachments())
                .labels(entity.getLabels())
                .threadId(entity.getThreadId())
                .snippet(entity.getSnippet())
                .sizeEstimate(entity.getSizeEstimate())
                .build();
    }
}
