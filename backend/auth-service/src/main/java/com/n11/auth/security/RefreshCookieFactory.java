package com.n11.auth.security;

import com.n11.auth.config.AuthCookieProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the Set-Cookie header for refresh-token issue and clear flows.
 *
 * <p>Storing the refresh token in an HttpOnly + Secure + SameSite=Lax cookie
 * scoped to the auth path is the OWASP-recommended replacement for keeping
 * it in localStorage: JS can't read it (XSS-safe), the browser only sends it
 * to /api/auth endpoints (no leak on every API call), and SameSite=Lax blocks
 * cross-site POSTs (CSRF-safe for the rotation endpoint).</p>
 */
@Component
public class RefreshCookieFactory {

    private final AuthCookieProperties props;

    public RefreshCookieFactory(AuthCookieProperties props) {
        this.props = props;
    }

    public ResponseCookie issue(String rawToken, long ttlSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(props.name(), rawToken)
                .httpOnly(true)
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path(props.path())
                .maxAge(Duration.ofSeconds(ttlSeconds));
        if (props.domain() != null && !props.domain().isBlank()) {
            builder.domain(props.domain());
        }
        return builder.build();
    }

    public ResponseCookie clear() {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(props.name(), "")
                .httpOnly(true)
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path(props.path())
                .maxAge(Duration.ZERO);
        if (props.domain() != null && !props.domain().isBlank()) {
            builder.domain(props.domain());
        }
        return builder.build();
    }

    public String name() {
        return props.name();
    }
}
