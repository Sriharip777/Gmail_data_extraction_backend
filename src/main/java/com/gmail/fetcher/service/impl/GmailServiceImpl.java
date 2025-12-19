package com.gmail.fetcher.service.impl;

import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.EmailFilterDTO;
import com.gmail.fetcher.dto.GmailCredentialsDTO;
import com.gmail.fetcher.entity.EmailMessage;
import com.gmail.fetcher.exception.EmailFetchException;
import com.gmail.fetcher.repository.EmailMessageRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of GmailService
 *
 * This service handles:
 * - Fetching emails from Gmail API
 * - Saving/updating emails in MongoDB
 * - Searching emails in database
 * - Filtering emails based on criteria
 * - Handling duplicates (checks by messageId)
 *
 * @author Gmail Fetcher Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GmailServiceImpl implements GmailService {

    private final EmailMessageRepository emailRepository;
    private final EmailFilterService filterService;
    private final GmailAuthUtil gmailAuthUtil;
    private final EmailParserUtil emailParserUtil;
    private final MongoTemplate mongoTemplate;

    /**
     * Fetch all emails from Gmail inbox
     *
     * @param credentials Gmail credentials (email + accessToken)
     * @return List of EmailDTO objects
     * @throws EmailFetchException if fetch fails
     */
    @Override
    public List<EmailDTO> fetchAllEmails(GmailCredentialsDTO credentials) {
        log.info("========================================");
        log.info("Fetching ALL emails for user: {}", credentials.getEmail());
        log.info("========================================");

        try {
            // Validate credentials
            if (!gmailAuthUtil.validateCredentials(credentials)) {
                throw new EmailFetchException("Invalid Gmail credentials provided");
            }

            // Get Gmail service
            Gmail service = gmailAuthUtil.getGmailService(credentials);

            // Fetch message list
            ListMessagesResponse response = service.users().messages()
                    .list("me")
                    .setMaxResults(100L)
                    .execute();

            List<EmailDTO> emails = new ArrayList<>();

            // Process each message
            if (response.getMessages() != null && !response.getMessages().isEmpty()) {
                log.info("Found {} message(s) in inbox", response.getMessages().size());

                for (com.google.api.services.gmail.model.Message messageHeader : response.getMessages()) {
                    try {
                        // Get full message details
                        Message fullMessage = service.users().messages()
                                .get("me", messageHeader.getId())
                                .setFormat("full")
                                .execute();

                        // Parse message to DTO
                        EmailDTO emailDTO = emailParserUtil.parseMessage(fullMessage);
                        emails.add(emailDTO);
                        log.debug("Parsed email: {} from {}", emailDTO.getSubject(), emailDTO.getFromEmail());

                    } catch (Exception e) {
                        log.error("Error parsing message {}: {}", messageHeader.getId(), e.getMessage());
                        // Continue processing other messages
                    }
                }
            } else {
                log.info("No messages found in inbox");
            }

            log.info("✅ Successfully fetched {} emails from Gmail", emails.size());
            return emails;

        } catch (Exception e) {
            log.error("❌ Error fetching emails from Gmail: {}", e.getMessage(), e);
            throw new EmailFetchException("Failed to fetch emails from Gmail", e);
        }
    }

    /**
     * Fetch emails from Gmail with specific filters
     *
     * @param credentials Gmail credentials
     * @param filter Filter criteria (sender, subject, date range, etc.)
     * @return List of filtered EmailDTO objects
     * @throws EmailFetchException if fetch fails
     */
    @Override
    public List<EmailDTO> fetchEmailsWithFilter(GmailCredentialsDTO credentials, EmailFilterDTO filter) {
        log.info("========================================");
        log.info("Fetching FILTERED emails for user: {}", credentials.getEmail());
        log.info("Filter: {}", filter);
        log.info("========================================");

        try {
            // Validate credentials
            if (!gmailAuthUtil.validateCredentials(credentials)) {
                throw new EmailFetchException("Invalid Gmail credentials provided");
            }

            // Validate filter
            if (!filterService.validateFilter(filter)) {
                throw new EmailFetchException("Invalid filter criteria provided");
            }

            // Get Gmail service
            Gmail service = gmailAuthUtil.getGmailService(credentials);

            // Build Gmail query from filter
            String query = filterService.buildGmailQuery(filter);
            log.debug("Built Gmail query: {}", query);

            // Fetch filtered messages
            ListMessagesResponse response = service.users().messages()
                    .list("me")
                    .setQ(query)
                    .setMaxResults(filter.getMaxResults() != null ?
                            filter.getMaxResults().longValue() : 100L)
                    .execute();

            List<EmailDTO> emails = new ArrayList<>();

            // Process each message
            if (response.getMessages() != null && !response.getMessages().isEmpty()) {
                log.info("Found {} filtered message(s)", response.getMessages().size());

                for (com.google.api.services.gmail.model.Message messageHeader : response.getMessages()) {
                    try {
                        // Get full message details
                        Message fullMessage = service.users().messages()
                                .get("me", messageHeader.getId())
                                .setFormat("full")
                                .execute();

                        // Parse message to DTO
                        EmailDTO emailDTO = emailParserUtil.parseMessage(fullMessage);
                        emails.add(emailDTO);
                        log.debug("Parsed filtered email: {}", emailDTO.getSubject());

                    } catch (Exception e) {
                        log.error("Error parsing message {}: {}", messageHeader.getId(), e.getMessage());
                        // Continue processing other messages
                    }
                }
            } else {
                log.info("No messages found matching filter criteria");
            }

            log.info("✅ Successfully fetched {} filtered emails", emails.size());
            return emails;

        } catch (Exception e) {
            log.error("❌ Error fetching filtered emails: {}", e.getMessage(), e);
            throw new EmailFetchException("Failed to fetch filtered emails", e);
        }
    }

    /**
     * Fetch a single email by message ID
     *
     * @param credentials Gmail credentials
     * @param messageId Gmail message ID
     * @return EmailDTO object
     * @throws EmailFetchException if fetch fails
     */
    @Override
    public EmailDTO fetchEmailById(GmailCredentialsDTO credentials, String messageId) {
        log.info("Fetching single email with ID: {}", messageId);

        try {
            // Validate credentials
            if (!gmailAuthUtil.validateCredentials(credentials)) {
                throw new EmailFetchException("Invalid Gmail credentials provided");
            }

            // Get Gmail service
            Gmail service = gmailAuthUtil.getGmailService(credentials);

            // Fetch message
            Message message = service.users().messages()
                    .get("me", messageId)
                    .setFormat("full")
                    .execute();

            if (message == null) {
                throw new EmailFetchException("Message not found: " + messageId);
            }

            // Parse message to DTO
            EmailDTO emailDTO = emailParserUtil.parseMessage(message);
            log.info("✅ Successfully fetched email: {}", emailDTO.getSubject());

            return emailDTO;

        } catch (Exception e) {
            log.error("❌ Error fetching email by ID {}: {}", messageId, e.getMessage(), e);
            throw new EmailFetchException("Failed to fetch email by ID: " + messageId, e);
        }
    }

    /**
     * Save emails to MongoDB database
     * Checks for duplicates by messageId and updates if exists, otherwise saves new
     *
     * @param emails List of EmailDTO objects to save
     */
    @Override
    @Transactional
    public void saveEmailsToDatabase(List<EmailDTO> emails) {
        log.info("========================================");
        log.info("Saving {} emails to MongoDB database", emails.size());
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
            try {
                // Check if email already exists by messageId
                Optional<EmailMessage> existing = emailRepository.findByMessageId(dto.getMessageId());

                if (existing.isPresent()) {
                    // Email exists - UPDATE it
                    EmailMessage entity = existing.get();
                    updateEntityFromDTO(entity, dto);
                    emailRepository.save(entity);
                    updatedCount++;
                    log.debug("✏️  Updated existing email: {} ({})", dto.getMessageId(), dto.getSubject());

                } else {
                    // Email is new - SAVE it
                    EmailMessage entity = convertToEntity(dto);
                    emailRepository.save(entity);
                    savedCount++;
                    log.debug("💾 Saved new email: {} ({})", dto.getMessageId(), dto.getSubject());
                }

            } catch (Exception e) {
                log.error("❌ Error saving email {}: {}", dto.getMessageId(), e.getMessage());
                skippedCount++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        log.info("========================================");
        log.info("Database Operation Summary:");
        log.info("  ✅ Saved:   {} new emails", savedCount);
        log.info("  ✏️  Updated: {} existing emails", updatedCount);
        log.info("  ⏭️  Skipped: {} emails (errors)", skippedCount);
        log.info("  ⏱️  Duration: {} ms", duration);
        log.info("  📊 Total in DB: {}", emailRepository.count());
        log.info("========================================");
    }

    /**
     * Search emails in MongoDB database with filters
     *
     * @param filter Filter criteria
     * @return List of matching EmailDTO objects
     */
    @Override
    public List<EmailDTO> searchEmailsInDatabase(EmailFilterDTO filter) {
        log.info("Searching emails in database");
        log.debug("Search filter: {}", filter);

        try {
            Query query = new Query();

            // Apply filters
            if (filter.getFromEmail() != null && !filter.getFromEmail().isEmpty()) {
                query.addCriteria(Criteria.where("fromEmail").regex(filter.getFromEmail(), "i"));
                log.debug("Added filter: fromEmail contains '{}'", filter.getFromEmail());
            }

            if (filter.getToEmail() != null && !filter.getToEmail().isEmpty()) {
                query.addCriteria(Criteria.where("toEmail").regex(filter.getToEmail(), "i"));
                log.debug("Added filter: toEmail contains '{}'", filter.getToEmail());
            }

            if (filter.getSubject() != null && !filter.getSubject().isEmpty()) {
                query.addCriteria(Criteria.where("subject").regex(filter.getSubject(), "i"));
                log.debug("Added filter: subject contains '{}'", filter.getSubject());
            }

            if (filter.getStartDate() != null && filter.getEndDate() != null) {
                query.addCriteria(Criteria.where("receivedDate")
                        .gte(filter.getStartDate())
                        .lte(filter.getEndDate()));
                log.debug("Added filter: date between {} and {}", filter.getStartDate(), filter.getEndDate());
            }

            if (filter.getIsRead() != null) {
                query.addCriteria(Criteria.where("isRead").is(filter.getIsRead()));
                log.debug("Added filter: isRead = {}", filter.getIsRead());
            }

            if (filter.getIsStarred() != null) {
                query.addCriteria(Criteria.where("isStarred").is(filter.getIsStarred()));
                log.debug("Added filter: isStarred = {}", filter.getIsStarred());
            }

            if (filter.getLabels() != null && !filter.getLabels().isEmpty()) {
                query.addCriteria(Criteria.where("labels").in(filter.getLabels()));
                log.debug("Added filter: labels in {}", filter.getLabels());
            }

            // Execute query
            List<EmailMessage> messages = mongoTemplate.find(query, EmailMessage.class);
            log.info("✅ Found {} emails in database", messages.size());

            // Convert to DTO
            return messages.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error searching emails in database: {}", e.getMessage(), e);
            throw new EmailFetchException("Failed to search emails in database", e);
        }
    }

    /**
     * Get Gmail service using credentials file
     * Used for desktop application flow
     *
     * @return Gmail service instance
     * @throws Exception if authentication fails
     */
    @Override
    public Gmail getGmailServiceWithFile() throws Exception {
        log.info("Getting Gmail service with credentials file");
        return gmailAuthUtil.getGmailServiceWithCredentialsFile();
    }

    /**
     * Sync all emails from Gmail to database
     * Complete fetch and save operation
     *
     * @param credentials Gmail credentials
     * @throws EmailFetchException if sync fails
     */
    @Override
    @Transactional
    public void syncEmailsFromGmail(GmailCredentialsDTO credentials) {
        log.info("========================================");
        log.info("Starting FULL EMAIL SYNC from Gmail");
        log.info("========================================");

        try {
            // Fetch emails from Gmail
            List<EmailDTO> emails = fetchAllEmails(credentials);

            // Save to database
            saveEmailsToDatabase(emails);

            log.info("✅ Email sync completed successfully!");
            log.info("   Total synced: {} emails", emails.size());

        } catch (Exception e) {
            log.error("❌ Error during email sync: {}", e.getMessage(), e);
            throw new EmailFetchException("Failed to sync emails from Gmail", e);
        }
    }

 // Helper methods
    private EmailMessage convertToEntity(EmailDTO dto) {
        return EmailMessage.builder()
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void updateEntityFromDTO(EmailMessage entity, EmailDTO dto) {
        entity.setSubject(dto.getSubject());
        entity.setFromEmail(dto.getFromEmail());
        entity.setToEmail(dto.getToEmail());
        entity.setBodyText(dto.getBodyText());
        entity.setBodyHtml(dto.getBodyHtml());
        entity.setIsRead(dto.getIsRead());
        entity.setIsStarred(dto.getIsStarred());
        entity.setLabels(dto.getLabels());
        entity.setUpdatedAt(LocalDateTime.now());
    }

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
