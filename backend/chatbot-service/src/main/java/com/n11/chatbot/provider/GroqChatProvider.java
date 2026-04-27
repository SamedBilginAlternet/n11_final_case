package com.n11.chatbot.provider;

import com.n11.chatbot.config.ChatbotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "n11.chatbot", name = "provider", havingValue = "GROQ")
@Slf4j
public class GroqChatProvider implements ChatProvider {

    private final ChatbotProperties props;
    private final RestClient client;

    public GroqChatProvider(ChatbotProperties props) {
        this.props = props;
        this.client = RestClient.builder()
                .baseUrl(props.groq().baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.groq().apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("GroqChatProvider active (model={} baseUrl={})",
                props.groq().model(), props.groq().baseUrl());
    }

    @Override
    public Reply complete(List<Turn> history, String systemPrompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (Turn t : history) {
            messages.add(Map.of("role", t.role(), "content", t.content()));
        }

        Map<String, Object> body = Map.of(
                "model", props.groq().model(),
                "max_tokens", props.groq().maxTokens(),
                "temperature", props.groq().temperature(),
                "messages", messages
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/openai/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return parseReply(response);
        } catch (Exception ex) {
            log.error("Groq API call failed", ex);
            return new Reply("Üzgünüm, asistan şu anda yanıt veremiyor. Birazdan tekrar dener misin?", 0);
        }
    }

    @SuppressWarnings("unchecked")
    private Reply parseReply(Map<String, Object> response) {
        if (response == null) return new Reply("(no content)", 0);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return new Reply("(no choices)", 0);

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = message != null ? String.valueOf(message.get("content")) : "(empty)";

        Integer tokens = null;
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        if (usage != null) {
            Object out = usage.get("completion_tokens");
            if (out instanceof Number n) tokens = n.intValue();
        }
        return new Reply(content, tokens);
    }
}
