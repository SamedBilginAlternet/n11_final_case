package com.n11.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("n11.notification")
public record NotificationProperties(
        String fromAddress,
        String fromName,
        String storefrontUrl,
        String adminPanelUrl,
        String adminAlertRecipient
) {
    public NotificationProperties {
        if (fromAddress == null || fromAddress.isBlank()) fromAddress = "no-reply@n11.local";
        if (fromName == null || fromName.isBlank()) fromName = "n11 Sipariş";
        if (storefrontUrl == null || storefrontUrl.isBlank()) storefrontUrl = "http://localhost:3000";
        if (adminPanelUrl == null || adminPanelUrl.isBlank()) adminPanelUrl = "http://localhost:3001";
        // adminAlertRecipient may legitimately be empty — when blank, the
        // low-stock listener will skip sending and just log a warning.
    }
}
