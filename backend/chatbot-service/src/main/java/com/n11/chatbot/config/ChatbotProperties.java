package com.n11.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "n11.chatbot")
public record ChatbotProperties(
        Provider provider,
        Anthropic anthropic,
        Catalog catalog
) {
    public enum Provider { CLAUDE, MOCK }

    public record Anthropic(
            String apiKey,
            String baseUrl,
            String model,
            int maxTokens,
            int timeoutSeconds
    ) {}

    public record Catalog(String productBaseUrl) {}
}
