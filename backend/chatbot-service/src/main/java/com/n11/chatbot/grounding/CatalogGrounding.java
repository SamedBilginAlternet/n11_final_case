package com.n11.chatbot.grounding;

import com.n11.chatbot.config.ChatbotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CatalogGrounding {

    private final RestClient client;

    public CatalogGrounding(ChatbotProperties props) {
        this.client = RestClient.builder().baseUrl(props.catalog().productBaseUrl()).build();
    }

    public String snapshotForPrompt() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.get()
                    .uri("/api/products?page=0&size=20")
                    .retrieve()
                    .body(Map.class);
            if (body == null) return "(catalog unavailable)";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("content", List.of());

            StringBuilder buf = new StringBuilder("Aktif katalog (özet):\n");
            for (Map<String, Object> p : items) {
                buf.append("- ").append(p.get("name"))
                        .append(" — ").append(p.get("price")).append(' ').append(p.get("currency"))
                        .append(" (kategori: ").append(p.get("categoryName")).append(", slug: ").append(p.get("slug")).append(")\n");
            }
            return buf.toString();
        } catch (Exception ex) {
            log.warn("Catalog grounding failed: {}", ex.getMessage());
            return "(catalog snapshot unavailable)";
        }
    }
}
