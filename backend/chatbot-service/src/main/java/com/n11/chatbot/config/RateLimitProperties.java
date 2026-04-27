package com.n11.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "n11.chatbot.rate-limit")
public record RateLimitProperties(
        int capacity,
        int windowSeconds
) {
    public RateLimitProperties {
        if (capacity <= 0) capacity = 20;
        if (windowSeconds <= 0) windowSeconds = 60;
    }
}
