package com.n11.chatbot.provider;

import com.n11.chatbot.config.ChatbotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "n11.chatbot", name = "provider", havingValue = "CLAUDE")
@Slf4j
public class ClaudeChatProvider implements ChatProvider {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final ChatbotProperties props;
    private final RestClient client;

    public ClaudeChatProvider(ChatbotProperties props) {
        this.props = props;
        this.client = RestClient.builder()
                .baseUrl(props.anthropic().baseUrl())
                .defaultHeader("x-api-key", props.anthropic().apiKey())
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .defaultHeader("content-type", "application/json")
                .build();
        log.info("ClaudeChatProvider active (model={} baseUrl={})",
                props.anthropic().model(), props.anthropic().baseUrl());
    }

    @Override
    public Reply complete(List<Turn> history, String systemPrompt) {
        List<Map<String, Object>> messages = history.stream()
                .map(t -> Map.<String, Object>of("role", t.role(), "content", t.content()))
                .toList();

        Map<String, Object> body = Map.of(
                "model", props.anthropic().model(),
                "max_tokens", props.anthropic().maxTokens(),
                "system", systemPrompt,
                "messages", messages
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return parseReply(response);
        } catch (Exception ex) {
            log.error("Claude API call failed", ex);
            return new Reply("Üzgünüm, asistan şu anda yanıt veremiyor. Birazdan tekrar dener misin?", 0);
        }
    }

    @SuppressWarnings("unchecked")
    private Reply parseReply(Map<String, Object> response) {
        if (response == null) return new Reply("(no content)", 0);

        List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) response.get("content");
        StringBuilder buf = new StringBuilder();
        if (contentBlocks != null) {
            for (Map<String, Object> block : contentBlocks) {
                Object text = block.get("text");
                if (text != null) buf.append(text);
            }
        }

        Integer tokens = null;
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        if (usage != null) {
            Object out = usage.get("output_tokens");
            if (out instanceof Number n) tokens = n.intValue();
        }
        return new Reply(buf.toString(), tokens);
    }

    @SuppressWarnings("unused")
    private Duration timeout() {
        return Duration.ofSeconds(props.anthropic().timeoutSeconds());
    }
}
