package com.n11.product.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.product.domain.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends the {seed, candidates} batch to Groq llama-3.1-8b-instant and gets
 * back a ranked top-N with one short Turkish "neden ilgini çekebilir"
 * sentence per item.
 *
 * <p>Strict JSON in/out: prompt asks for a JSON array, response is parsed
 * with Jackson.  If parsing fails we return an empty list; the caller
 * (RecommendationService) drops the explanations and serves products by
 * the original candidate order.  Free tier rate limits (30 req/min on
 * Groq's 8b-instant) are plenty for a single batch per product detail
 * page hit, especially with Redis caching the result for 5 minutes.</p>
 */
@Component
@ConditionalOnProperty(prefix = "n11.recommendations.groq", name = "api-key", matchIfMissing = false)
@Slf4j
public class GroqRecommendationClient {

    private final RecommendationProperties props;
    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public GroqRecommendationClient(RecommendationProperties props) {
        this.props = props;
        this.client = RestClient.builder()
                .baseUrl(props.groq().baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.groq().apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("GroqRecommendationClient active (model={})", props.groq().model());
    }

    public record Ranked(Long productId, String reason) {}

    public List<Ranked> rerank(Product seed, List<Product> candidates, int topN) {
        if (candidates.isEmpty()) return List.of();

        String prompt = buildPrompt(seed, candidates, topN);

        Map<String, Object> body = Map.of(
                "model", props.groq().model(),
                "max_tokens", props.groq().maxTokens(),
                "temperature", props.groq().temperature(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "Türkçe konuşan ve Türk e-ticaret katalogunu iyi bilen bir öneri "
                                        + "asistanısın. Yanıtın MUTLAKA tek bir JSON object olmalı, başka açıklama yok."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/openai/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return parseRanked(response);
        } catch (Exception ex) {
            log.warn("Groq rerank failed (seedId={}): {}", seed.getId(), ex.getMessage());
            return List.of();
        }
    }

    private String buildPrompt(Product seed, List<Product> candidates, int topN) {
        StringBuilder sb = new StringBuilder();
        sb.append("Müşteri şu ürüne baktı:\n")
          .append("- ID ").append(seed.getId())
          .append(" | ").append(seed.getName())
          .append(" | kategori: ").append(seed.getCategory().getName())
          .append(" | fiyat: ").append(seed.getPrice()).append(" ").append(seed.getCurrency())
          .append("\n\nAday ürünler:\n");
        for (Product c : candidates) {
            sb.append("- ID ").append(c.getId())
              .append(" | ").append(c.getName())
              .append(" | kategori: ").append(c.getCategory().getName())
              .append(" | fiyat: ").append(c.getPrice()).append(" ").append(c.getCurrency())
              .append(" | rating: ").append(c.getRatingAverage())
              .append("\n");
        }
        sb.append("\nGörev: Müşterinin baktığı ürünle en alakalı en fazla ").append(topN)
          .append(" adet adayı seç ve her biri için TEK CÜMLELİK Türkçe \"neden ilgini çekebilir\" açıklaması yaz. ")
          .append("Açıklamada ürün adını tekrar etme, alakanın sebebini söyle (örn. \"telefonunla uyumlu\", \"aynı stilde tamamlayıcı\", \"daha güçlü versiyonu\"). ")
          .append("Yanıtı şu JSON şemasıyla ver: ")
          .append("{\"items\":[{\"productId\":<long>,\"reason\":\"<cümle>\"}]}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Ranked> parseRanked(Map<String, Object> response) {
        if (response == null) return List.of();
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return List.of();
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) return List.of();
        String content = String.valueOf(message.get("content"));

        try {
            Map<String, Object> parsed = mapper.readValue(content, Map.class);
            Object items = parsed.get("items");
            if (!(items instanceof List<?> list)) return List.of();
            List<Ranked> out = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Object pid = m.get("productId");
                Object reason = m.get("reason");
                if (pid instanceof Number n && reason != null) {
                    out.add(new Ranked(n.longValue(), String.valueOf(reason)));
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn("Groq response not valid JSON: {}", ex.getMessage());
            return List.of();
        }
    }
}
