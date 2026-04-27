package com.n11.auth.api.dto;

import java.time.Instant;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant issuedAt,
        String refreshToken,
        long refreshExpiresIn,
        UserDto user
) {}
