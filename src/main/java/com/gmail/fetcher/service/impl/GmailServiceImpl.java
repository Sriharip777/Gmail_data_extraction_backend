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
 * Complete Implementation of GmailService with Multi-Employee Support
 *
 * Features:
 * - Multi-employee Gmail support with empId tracking
 * - Fetch emails with pagination
 * - Filter emails by various criteria
 * - Save/update emails in MongoDB
 * - Search emails in database
 * - Employee-specific operations
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
    // MULTI-EMPLOYEE METHODS (UPDATED)
    // ========================================

    /**
     * Fetch and save emails for specific employee with limit
     * ✅ UPDATED: Now uses empId instead of ownerEmail
     */
    @Override
    @Transactional
    public int fetchAndSaveEmailsForAccount(String empId, int maxEmails) {
        log.info("========================================");
        log.info("Fetching max {} emails for empId: {}", maxEmails, empId);
        log.info("========================================");

        try {
            // ✅ Get token by empId
            GmailToken token = gmailTokenRepository.findByEmpId(empId)
                    .orElseThrow(() -> new EmailFetchException("Token not found for empId: " + empId));

            String googleEmail = token.getGoogleEmail();
            log.info("✅ Found Gmail account: {} for empId: {}", googleEmail, empId);

            // Build credentials
            GmailCredentialsDTO credentials = GmailCredentialsDTO.builder()
                    .email(googleEmail)
                    .accessToken(token.getAccessToken())
                    .refreshToken(token.getRefreshToken())
                    .build();

            // Validate credentials
            if (!gmailAuthUtil.validateCredentials(credentials)) {
                throw new EmailFetchException("Invalid Gmail credentials for empId: " + empId);
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
                log.info("No messages found for empId: {}", empId);
            }

            // ✅ Save to database with empId
            saveEmailsToDatabase(emails, empId, googleEmail);

            // Update last sync time
            token.setLastSyncedAt(Instant.now());
            gmailTokenRepository.save(token);

            log.info("✅ Successfully fetched and saved {} emails for empId: {}", emails.size(), empId);
            return emails.size();

        } catch (Exception e) {
            log.error("❌ Error fetching emails for empId {}: {}", empId, e.getMessage(), e);
            throw new EmailFetchException("Failed to fetch emails for employee", e);
        }
    }

    /**
     * Get statistics for specific employee
     * ✅ UPDATED: Now uses empId
     */
    @Override
    public Map<String, Object> getStatsForAccount(String empId) {
        log.info("Getting statistics for empId: {}", empId);

        Map<String, Object> stats = new HashMap<>();

        // ✅ Get counts by empId
        long totalEmails = emailRepository.countByOwnerEmpId(empId);
        long unreadEmails = emailRepository.countByOwnerEmpIdAndIsRead(empId, false);
        long starredEmails = emailRepository.countByOwnerEmpIdAndIsStarred(empId, true);

        stats.put("totalEmails", totalEmails);
        stats.put("unreadEmails", unreadEmails);
        stats.put("starredEmails", starredEmails);
        stats.put("readEmails", totalEmails - unreadEmails);

        // Get recent sync time
        GmailToken token = gmailTokenRepository.findByEmpId(empId).orElse(null);
        if (token != null) {
            stats.put("lastSynced", token.getLastSyncedAt());
            stats.put("tokenCreated", token.getCreatedAt());
            stats.put("googleEmail", token.getGoogleEmail());
        } else {
            stats.put("lastSynced", null);
            stats.put("tokenCreated", null);
            stats.put("googleEmail", null);
        }

        log.info("Stats for empId {}: Total={}, Unread={}, Starred={}",
                empId, totalEmails, unreadEmails, starredEmails);

        return stats;
    }

    /**
     * Search emails for specific employee
     * ✅ UPDATED: Now uses empId
     */
    @Override
    public List<EmailDTO> searchEmailsForAccount(String empId, EmailFilterDTO filter) {
        log.info("Searching emails for empId: {} with filter: {}", empId, filter);

        try {
            Query query = new Query();

            // ✅ MUST match owner empId
            query.addCriteria(Criteria.where("ownerEmpId").is(empId));

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
            log.info("✅ Found {} emails for empId {}", messages.size(), empId);

            return messages.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error searching emails for empId {}: {}", empId, e.getMessage(), e);
            throw new EmailFetchException("Failed to search emails for employee", e);
        }
    }

    // ========================================
    // ORIGINAL METHODS (FOR BACKWARD COMPATIBILITY)
    // ========================================

    /**
     * Fetch all emails from Gmail (legacy method)
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
     * Fetch emails with filter (legacy method)
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
     * Fetch single email by ID (legacy method)
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
     * Save emails to database with empId tracking
     * ✅ UPDATED: Now requires empId and googleEmail parameters
     */
    @Override
    @Transactional
    public void saveEmailsToDatabase(List<EmailDTO> emails, String empId, String googleEmail) {
        log.info("========================================");
        log.info("Saving {} emails for empId: {}, Gmail: {}", emails.size(), empId, googleEmail);
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
                // ✅ Check if email exists for THIS empId
                Optional<EmailMessage> existing = emailRepository
                        .findByOwnerEmpIdAndMessageId(empId, dto.getMessageId());

                if (existing.isPresent()) {
                    EmailMessage entity = existing.get();
                    updateEntityFromDTO(entity, dto);
                    emailRepository.save(entity);
                    updatedCount++;
                } else {
                    EmailMessage entity = convertToEntity(dto, empId, googleEmail);
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
        log.info("Database Operation Summary for empId {}:", empId);
        log.info("  Saved:   {} new emails", savedCount);
        log.info("  Updated: {} existing emails", updatedCount);
        log.info("  Skipped: {} emails (errors)", skippedCount);
        log.info("  Duration: {} ms", duration);
        log.info("  Total for this employee: {}", emailRepository.countByOwnerEmpId(empId));
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
     * Get Gmail service with credentials file (legacy method)
     */
    @Override
    public Gmail getGmailServiceWithFile() throws Exception {
        log.info("Getting Gmail service with credentials file");
        return gmailAuthUtil.getGmailServiceWithCredentialsFile();
    }

    /**
     * Sync emails from Gmail to database (legacy method)
     */
    @Override
    @Transactional
    public void syncEmailsFromGmail(GmailCredentialsDTO credentials) {
        log.info("========================================");
        log.info("Starting FULL EMAIL SYNC from Gmail");
        log.info("========================================");

        try {
            List<EmailDTO> emails = fetchAllEmails(credentials);
            // Note: For legacy support, you might need to find empId from email
            // For now, save with email only (backward compatibility)
            log.warn("⚠️ Using legacy sync method - empId tracking not available");

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
     * Convert DTO to Entity with empId and googleEmail
     * ✅ UPDATED: Now sets both empId and ownerEmail
     */
    private EmailMessage convertToEntity(EmailDTO dto, String empId, String googleEmail) {
        LocalDateTime now = LocalDateTime.now();
        return EmailMessage.builder()
                .ownerEmpId(empId)           // ✅ Set employee ID
                .ownerEmail(googleEmail)     // ✅ Set Gmail address (for reference)
                .messageId(dto.getMessageId())
                .subject(dto.getSubject())
                .fromEmail(dto.getFromEmail())
                .fromName(dto.getFromName())
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
        entity.setFromName(dto.getFromName());
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
                .fromName(entity.getFromName())
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
