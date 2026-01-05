package com.gmail.fetcher.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for Email
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDTO {

    private String messageId;
    private String subject;
    private String fromEmail;
    private String fromName;
    private String toEmail;
    private String ccEmail;
    private String bccEmail;
    private String bodyText;
    private String bodyHtml;
    private LocalDateTime receivedDate;
    private Boolean isRead;
    private Boolean isStarred;
    private Boolean hasAttachments;
    private List<String> labels;
    private String threadId;
    private String snippet;
    private Integer sizeEstimate;
}
