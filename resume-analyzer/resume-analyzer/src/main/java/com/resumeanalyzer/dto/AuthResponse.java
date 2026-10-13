package com.resumeanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of POST /api/auth/register and POST /api/auth/login.
 *
 * `token` is a signed HS256 JWT; send it back as
 * `Authorization: Bearer <token>`. The password (or its hash) is never included.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String message;
    private UserResponse user;
    private String token;
    private String tokenType;
    private Long expiresInMs;
}
