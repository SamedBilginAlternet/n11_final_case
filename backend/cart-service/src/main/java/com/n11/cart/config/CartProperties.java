package com.n11.cart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "n11")
public record CartProperties(
        Jwt jwt,
        Services services
) {
    public record Jwt(String secret, String issuer) {}
    public record Services(String productBaseUrl) {}
}
