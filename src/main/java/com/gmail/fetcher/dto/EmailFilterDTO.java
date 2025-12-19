package com.gmail.fetcher.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for filtering emails
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailFilterDTO {

    private String fromEmail;
    private String toEmail;
    private String subject;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean isRead;
    private Boolean isStarred;
    private List<String> labels;
    private Integer maxResults;
    private String searchQuery;
}
