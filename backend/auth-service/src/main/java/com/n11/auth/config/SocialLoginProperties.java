package com.n11.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("n11.social-login")
public record SocialLoginProperties(
        String frontendBaseUrl,
        String successPath,
        String failurePath,
        Provider google,
        Provider github
) {

    public SocialLoginProperties {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) frontendBaseUrl = "http://localhost:3000";
        if (successPath == null || successPath.isBlank()) successPath = "/auth/callback";
        if (failurePath == null || failurePath.isBlank()) failurePath = "/login";
        if (google == null) google = new Provider(null, null);
        if (github == null) github = new Provider(null, null);
    }

    public boolean googleEnabled() {
        return google != null && google.clientId != null && !google.clientId.isBlank();
    }

    public boolean githubEnabled() {
        return github != null && github.clientId != null && !github.clientId.isBlank();
    }

    public boolean anyEnabled() {
        return googleEnabled() || githubEnabled();
    }

    public record Provider(String clientId, String clientSecret) {}
}
