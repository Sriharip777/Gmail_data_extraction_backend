package com.gmail.fetcher.service;


import com.gmail.fetcher.dto.EmailDTO;
import com.gmail.fetcher.dto.EmailFilterDTO;

import java.util.List;

/**
 * Service interface for email filtering
 */
public interface EmailFilterService {

    /**
     * Filter emails based on criteria
     */
    List<EmailDTO> filterEmails(List<EmailDTO> emails, EmailFilterDTO filter);

    /**
     * Build Gmail query string from filter
     */
    String buildGmailQuery(EmailFilterDTO filter);

    /**
     * Validate filter criteria
     */
    boolean validateFilter(EmailFilterDTO filter);
}

