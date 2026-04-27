package com.n11.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "n11")
public record SecurityProperties(Jwt jwt) {
    public record Jwt(String secret, String issuer) {}
}
