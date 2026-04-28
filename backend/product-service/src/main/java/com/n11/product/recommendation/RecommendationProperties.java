package com.n11.product.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("n11.recommendations")
public record RecommendationProperties(
        boolean enabled,
        Groq groq,
        OrderService orderService
) {
    public RecommendationProperties {
        if (groq == null) groq = new Groq("", "https://api.groq.com", "llama-3.1-8b-instant", 600, 0.4, 8);
        if (orderService == null) orderService = new OrderService("http://localhost:8084", 3);
    }

    /** True when a Groq API key is configured — otherwise we fall back to plain SQL ordering. */
    public boolean groqEnabled() {
        return groq != null && groq.apiKey != null && !groq.apiKey.isBlank();
    }

    public record Groq(
            String apiKey,
            String baseUrl,
            String model,
            int maxTokens,
            double temperature,
            int timeoutSeconds
    ) {}

    public record OrderService(String baseUrl, int timeoutSeconds) {}
}
