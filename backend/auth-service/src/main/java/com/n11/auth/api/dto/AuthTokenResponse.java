package com.n11.auth.api.dto;

import java.time.Instant;

/**
 * Response body for /login, /refresh and the OAuth callback.  The refresh
 * token is intentionally absent — it now travels exclusively in an HttpOnly
 * cookie set on the same response (see {@code RefreshCookieFactory}).
 */
public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant issuedAt,
        UserDto user
) {}
