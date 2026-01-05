package com.gmail.fetcher.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB Document Entity for Email Messages
 * UPDATED: Added ownerEmail to support multiple Gmail accounts
 */
@Document(collection = "email_messages")
@CompoundIndex(name = "owner_message_idx", def = "{'owner_email': 1, 'message_id': 1}", unique = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailMessage {

    @Id
    private String id;

    @Indexed
    private String ownerEmpId;  // Employee ID who owns this email

    // NEW: Owner of this email (which Gmail account it belongs to)
    @Indexed
    @Field("owner_email")
    private String ownerEmail;

    @Field("message_id")
    private String messageId;

    @Field("subject")
    private String subject;

    @Indexed
    @Field("from_email")
    private String fromEmail;

    private String fromName;

    @Field("to_email")
    private String toEmail;

    @Field("cc_email")
    private String ccEmail;

    @Field("bcc_email")
    private String bccEmail;

    @Field("body_text")
    private String bodyText;

    @Field("body_html")
    private String bodyHtml;

    @Indexed
    @Field("received_date")
    private LocalDateTime receivedDate;

    @Field("is_read")
    private Boolean isRead;

    @Field("is_starred")
    private Boolean isStarred;

    @Field("has_attachments")
    private Boolean hasAttachments;

    @Field("labels")
    private List<String> labels;

    @Field("thread_id")
    private String threadId;

    @Field("snippet")
    private String snippet;

    @Field("size_estimate")
    private Integer sizeEstimate;

    @Field("internal_date")
    private Long internalDate;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;
}
