package com.n11.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "n11.iyzico")
public record PaymentProperties(
        boolean enabled,
        String apiKey,
        String secretKey,
        String baseUrl,
        Failure failure
) {
    public record Failure(boolean simulate, double rate) {}
}
