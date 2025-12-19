package com.gmail.fetcher.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Gmail credentials
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GmailCredentialsDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Access token is required")
    private String accessToken;

    private String refreshToken;
    private String clientId;
    private String clientSecret;
}

