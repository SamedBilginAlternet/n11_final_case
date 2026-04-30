package com.n11.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh-token cookie attributes.  HttpOnly is hard-coded (the whole point);
 * Secure / SameSite / Path / Name come from config so dev (HTTP) can relax
 * Secure without code changes and prod can pin the cookie to the auth path.
 */
@ConfigurationProperties(prefix = "n11.auth.cookie")
public record AuthCookieProperties(
        String name,
        String path,
        String sameSite,
        Boolean secure,
        String domain
) {
    public AuthCookieProperties {
        if (name == null || name.isBlank()) name = "n11_refresh";
        if (path == null || path.isBlank()) path = "/api/auth";
        if (sameSite == null || sameSite.isBlank()) sameSite = "Lax";
        if (secure == null) secure = Boolean.TRUE;
        // domain may legitimately stay null → host-only cookie (recommended).
    }
}
