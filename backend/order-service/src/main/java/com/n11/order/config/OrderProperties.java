package com.n11.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "n11")
public record OrderProperties(
        Jwt jwt,
        Services services,
        Internal internal
) {
    public record Jwt(String secret, String issuer) {}
    public record Services(String cartBaseUrl, String authBaseUrl, String productBaseUrl) {}
    /** Shared service-to-service token for product-service stock saga calls. */
    public record Internal(String apiToken) {}
}
