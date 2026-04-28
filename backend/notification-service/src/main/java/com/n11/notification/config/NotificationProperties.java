package com.n11.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("n11.notification")
public record NotificationProperties(
        String fromAddress,
        String fromName,
        String storefrontUrl
) {
    public NotificationProperties {
        if (fromAddress == null || fromAddress.isBlank()) fromAddress = "no-reply@n11.local";
        if (fromName == null || fromName.isBlank()) fromName = "n11 Sipariş";
        if (storefrontUrl == null || storefrontUrl.isBlank()) storefrontUrl = "http://localhost:3000";
    }
}
