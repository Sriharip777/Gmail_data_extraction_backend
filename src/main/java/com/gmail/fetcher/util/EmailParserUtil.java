package com.gmail.fetcher.util;


import com.gmail.fetcher.dto.EmailDTO;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

/**
 * Utility class for parsing Gmail messages
 */
@Component
@Slf4j
public class EmailParserUtil {

    /**
     * Parse Gmail Message to EmailDTO
     */
    public EmailDTO parseMessage(Message message) {
        log.debug("Parsing message: {}", message.getId());

        EmailDTO.EmailDTOBuilder builder = EmailDTO.builder();

        // Set message ID
        builder.messageId(message.getId());
        builder.threadId(message.getThreadId());
        builder.snippet(message.getSnippet());

        // FIX: Convert Integer to Long
        if (message.getSizeEstimate() != null) {
            builder.sizeEstimate(message.getSizeEstimate());
        }

        // Parse headers
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            List<MessagePartHeader> headers = message.getPayload().getHeaders();

            for (MessagePartHeader header : headers) {
                String name = header.getName().toLowerCase();
                String value = header.getValue();

                switch (name) {
                    case "from":
                        builder.fromEmail(extractEmail(value));
                        break;
                    case "to":
                        builder.toEmail(extractEmail(value));
                        break;
                    case "cc":
                        builder.ccEmail(extractEmail(value));
                        break;
                    case "bcc":
                        builder.bccEmail(extractEmail(value));
                        break;
                    case "subject":
                        builder.subject(value != null ? value : "(No Subject)");
                        break;
                }
            }
        }

        // Set received date
        if (message.getInternalDate() != null) {
            LocalDateTime receivedDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(message.getInternalDate()),
                    ZoneId.systemDefault()
            );
            builder.receivedDate(receivedDate);
        }

        // Set labels and read status
        if (message.getLabelIds() != null) {
            builder.labels(message.getLabelIds());
            builder.isRead(!message.getLabelIds().contains("UNREAD"));
            builder.isStarred(message.getLabelIds().contains("STARRED"));
        }

        // Extract body
        if (message.getPayload() != null) {
            String bodyText = extractBodyText(message.getPayload());
            String bodyHtml = extractBodyHtml(message.getPayload());

            builder.bodyText(bodyText);
            builder.bodyHtml(bodyHtml);
        }

        // Check for attachments
        builder.hasAttachments(hasAttachments(message.getPayload()));

        EmailDTO emailDTO = builder.build();
        log.debug("Successfully parsed message: {}", emailDTO.getSubject());

        return emailDTO;
    }


    /**
     * Extract email address from header value
     */
    private String extractEmail(String headerValue) {
        if (headerValue == null || headerValue.isEmpty()) {
            return "";
        }

        // Extract email from "Name <email@example.com>" format
        if (headerValue.contains("<") && headerValue.contains(">")) {
            int start = headerValue.indexOf("<") + 1;
            int end = headerValue.indexOf(">");
            return headerValue.substring(start, end).trim();
        }

        return headerValue.trim();
    }

    /**
     * Extract plain text body from message
     */
    private String extractBodyText(MessagePart part) {
        if (part == null) {
            return "";
        }

        // Check if this part is text/plain
        if (part.getMimeType() != null && part.getMimeType().equals("text/plain")) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                return decodeBody(part.getBody().getData());
            }
        }

        // Recursively search in parts
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String body = extractBodyText(subPart);
                if (body != null && !body.isEmpty()) {
                    return body;
                }
            }
        }

        return "";
    }

    /**
     * Extract HTML body from message
     */
    private String extractBodyHtml(MessagePart part) {
        if (part == null) {
            return "";
        }

        // Check if this part is text/html
        if (part.getMimeType() != null && part.getMimeType().equals("text/html")) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                return decodeBody(part.getBody().getData());
            }
        }

        // Recursively search in parts
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String body = extractBodyHtml(subPart);
                if (body != null && !body.isEmpty()) {
                    return body;
                }
            }
        }

        return "";
    }

    /**
     * Decode base64 encoded body
     */
    private String decodeBody(String encodedBody) {
        try {
            byte[] bodyBytes = Base64.getUrlDecoder().decode(encodedBody);
            return new String(bodyBytes, "UTF-8");
        } catch (Exception e) {
            log.error("Error decoding body: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Check if message has attachments
     */
    private boolean hasAttachments(MessagePart part) {
        if (part == null) {
            return false;
        }

        // Check if this part has a filename (attachment indicator)
        if (part.getFilename() != null && !part.getFilename().isEmpty()) {
            return true;
        }

        // Recursively check parts
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                if (hasAttachments(subPart)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Extract all attachment filenames
     */
    public List<String> extractAttachmentNames(MessagePart part) {
        List<String> attachments = new java.util.ArrayList<>();

        if (part == null) {
            return attachments;
        }

        if (part.getFilename() != null && !part.getFilename().isEmpty()) {
            attachments.add(part.getFilename());
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                attachments.addAll(extractAttachmentNames(subPart));
            }
        }

        return attachments;
    }
}
