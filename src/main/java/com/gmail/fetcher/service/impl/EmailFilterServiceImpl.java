package com.gmail.fetcher.service.impl;


import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.EmailFilterDTO;
import com.gmail.fetcher.service.EmailFilterService;
import com.gmail.fetcher.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of EmailFilterService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailFilterServiceImpl implements EmailFilterService {

    private final DateUtil dateUtil;

    @Override
    public List<EmailDTO> filterEmails(List<EmailDTO> emails, EmailFilterDTO filter) {
        log.debug("Filtering {} emails", emails.size());

        return emails.stream()
                .filter(email -> matchesFilter(email, filter))
                .collect(Collectors.toList());
    }

    @Override
    public String buildGmailQuery(EmailFilterDTO filter) {
        StringBuilder query = new StringBuilder();

        if (filter.getFromEmail() != null && !filter.getFromEmail().isEmpty()) {
            query.append("from:").append(filter.getFromEmail()).append(" ");
        }

        if (filter.getToEmail() != null && !filter.getToEmail().isEmpty()) {
            query.append("to:").append(filter.getToEmail()).append(" ");
        }

        if (filter.getSubject() != null && !filter.getSubject().isEmpty()) {
            query.append("subject:").append(filter.getSubject()).append(" ");
        }

        if (filter.getStartDate() != null) {
            query.append("after:").append(dateUtil.formatForGmail(filter.getStartDate())).append(" ");
        }

        if (filter.getEndDate() != null) {
            query.append("before:").append(dateUtil.formatForGmail(filter.getEndDate())).append(" ");
        }

        if (filter.getIsRead() != null) {
            query.append(filter.getIsRead() ? "is:read " : "is:unread ");
        }

        if (filter.getIsStarred() != null && filter.getIsStarred()) {
            query.append("is:starred ");
        }

        if (filter.getLabels() != null && !filter.getLabels().isEmpty()) {
            for (String label : filter.getLabels()) {
                query.append("label:").append(label).append(" ");
            }
        }

        if (filter.getSearchQuery() != null && !filter.getSearchQuery().isEmpty()) {
            query.append(filter.getSearchQuery()).append(" ");
        }

        String finalQuery = query.toString().trim();
        log.debug("Built Gmail query: {}", finalQuery);

        return finalQuery;
    }

    @Override
    public boolean validateFilter(EmailFilterDTO filter) {
        if (filter == null) {
            return false;
        }

        if (filter.getStartDate() != null && filter.getEndDate() != null) {
            if (filter.getStartDate().isAfter(filter.getEndDate())) {
                log.error("Start date cannot be after end date");
                return false;
            }
        }

        if (filter.getMaxResults() != null && filter.getMaxResults() < 1) {
            log.error("Max results must be at least 1");
            return false;
        }

        return true;
    }

    private boolean matchesFilter(EmailDTO email, EmailFilterDTO filter) {
        if (filter.getFromEmail() != null &&
                !email.getFromEmail().toLowerCase().contains(filter.getFromEmail().toLowerCase())) {
            return false;
        }

        if (filter.getSubject() != null &&
                !email.getSubject().toLowerCase().contains(filter.getSubject().toLowerCase())) {
            return false;
        }

        if (filter.getStartDate() != null &&
                email.getReceivedDate().isBefore(filter.getStartDate())) {
            return false;
        }

        if (filter.getEndDate() != null &&
                email.getReceivedDate().isAfter(filter.getEndDate())) {
            return false;
        }

        if (filter.getIsRead() != null &&
                !filter.getIsRead().equals(email.getIsRead())) {
            return false;
        }

        return true;
    }
}
