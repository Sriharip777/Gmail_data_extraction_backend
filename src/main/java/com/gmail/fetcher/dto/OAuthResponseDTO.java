package com.gmail.fetcher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for OAuth response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthResponseDTO {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private String scope;
    private String message;
    private Boolean success;
}

