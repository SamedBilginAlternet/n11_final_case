package com.n11.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "n11.jwt")
public record JwtProperties(String secret, String issuer) {}
