package com.n11.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("n11.social-login")
public record SocialLoginProperties(
        String frontendBaseUrl,
        String successPath,
        String failurePath,
        // When set, used verbatim as the OAuth2 redirect-uri template. Otherwise
        // Spring derives one from `{baseUrl}` (request scheme/host) — which gives
        // the wrong scheme behind a TLS-terminating proxy whose X-Forwarded-Proto
        // is mangled by an upstream gateway. Set to e.g.
        //   https://example.com/api/auth/oauth2/callback/{registrationId}
        // in prod to bypass that derivation.
        String redirectUriTemplate,
        Provider google
) {

    public SocialLoginProperties {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) frontendBaseUrl = "http://localhost:3000";
        if (successPath == null || successPath.isBlank()) successPath = "/auth/callback";
        if (failurePath == null || failurePath.isBlank()) failurePath = "/login";
        if (redirectUriTemplate == null || redirectUriTemplate.isBlank()) {
            redirectUriTemplate = "{baseUrl}/api/auth/oauth2/callback/{registrationId}";
        }
        if (google == null) google = new Provider(null, null);
    }

    public boolean googleEnabled() {
        return google != null && google.clientId != null && !google.clientId.isBlank();
    }

    public boolean anyEnabled() {
        return googleEnabled();
    }

    public record Provider(String clientId, String clientSecret) {}
}
