package com.gmail.fetcher.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for email processing
 */
@Configuration
@ConfigurationProperties(prefix = "email.processing")
@Data
public class EmailProcessingProperties {

    private Integer batchSize = 50;
    private Integer threadPoolSize = 5;
    private Integer maxEmailAgeDays = 365;
    private Boolean enableHtmlParsing = true;
    private Boolean saveAttachments = false;
    private String attachmentsPath = "attachments/";
}
