package com.n11.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "n11.slack")
public record NotificationProperties(
        boolean enabled,
        String webhookUrl,
        String channel,
        String username
) {}
