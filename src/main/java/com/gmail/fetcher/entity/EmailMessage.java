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

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB Document Entity for Email Messages
 */
@Document(collection = "email_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailMessage {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("message_id")
    private String messageId;

    @Field("subject")
    private String subject;

    @Indexed
    @Field("from_email")
    private String fromEmail;

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
    private Long sizeEstimate;

    @Field("internal_date")
    private Long internalDate;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;
}
