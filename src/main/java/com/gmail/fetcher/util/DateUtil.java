package com.gmail.fetcher.util;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for date operations
 */
@Component
@Slf4j
public class DateUtil {

    // Gmail API date format (yyyy/MM/dd)
    private static final DateTimeFormatter GMAIL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd");

    // Standard ISO format
    private static final DateTimeFormatter ISO_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Display format
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    /**
     * Format LocalDateTime for Gmail API query
     * Example: 2025/12/18
     */
    public String formatForGmail(LocalDateTime date) {
        if (date == null) {
            return "";
        }

        try {
            return date.format(GMAIL_DATE_FORMAT);
        } catch (Exception e) {
            log.error("Error formatting date for Gmail: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Format LocalDate for Gmail API query
     */
    public String formatForGmail(LocalDate date) {
        if (date == null) {
            return "";
        }

        try {
            return date.format(GMAIL_DATE_FORMAT);
        } catch (Exception e) {
            log.error("Error formatting date for Gmail: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Parse Gmail date string to LocalDateTime
     */
    public LocalDateTime parseFromGmail(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }

        try {
            LocalDate localDate = LocalDate.parse(dateString, GMAIL_DATE_FORMAT);
            return localDate.atStartOfDay();
        } catch (DateTimeParseException e) {
            log.error("Error parsing Gmail date string '{}': {}", dateString, e.getMessage());
            return null;
        }
    }

    /**
     * Format LocalDateTime for display
     * Example: 18-Dec-2025 12:30:45
     */
    public String formatForDisplay(LocalDateTime date) {
        if (date == null) {
            return "";
        }

        try {
            return date.format(DISPLAY_FORMAT);
        } catch (Exception e) {
            log.error("Error formatting date for display: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Get current timestamp
     */
    public LocalDateTime getCurrentTimestamp() {
        return LocalDateTime.now();
    }

    /**
     * Get date N days ago
     */
    public LocalDateTime getDaysAgo(int days) {
        return LocalDateTime.now().minusDays(days);
    }

    /**
     * Get date N months ago
     */
    public LocalDateTime getMonthsAgo(int months) {
        return LocalDateTime.now().minusMonths(months);
    }

    /**
     * Check if date is within range
     */
    public boolean isDateInRange(LocalDateTime date, LocalDateTime start, LocalDateTime end) {
        if (date == null || start == null || end == null) {
            return false;
        }

        return !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * Convert epoch milliseconds to LocalDateTime
     */
    public LocalDateTime fromEpochMillis(long epochMillis) {
        try {
            return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(epochMillis),
                    java.time.ZoneId.systemDefault()
            );
        } catch (Exception e) {
            log.error("Error converting epoch millis to LocalDateTime: {}", e.getMessage());
            return null;
        }
    }
}

