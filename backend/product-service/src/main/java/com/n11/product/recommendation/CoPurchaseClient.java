package com.n11.product.recommendation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;

/**
 * Thin wrapper over order-service's /internal/co-purchases endpoint.
 *
 * <p>Order-service may be unreachable (network blip, deploying) — when it is,
 * we return an empty list and let RecommendationService fall back to the
 * same-category candidate set.  The recommendation panel must never break
 * the product detail page, so every error path here is non-throwing.</p>
 */
@Component
@Slf4j
public class CoPurchaseClient {

    private final RestClient client;

    public CoPurchaseClient(RecommendationProperties props) {
        this.client = RestClient.builder()
                .baseUrl(props.orderService().baseUrl())
                .build();
    }

    public record CoPurchase(Long productId, String productName, long occurrences) {}

    public List<CoPurchase> topCoPurchasesFor(Long productId, int limit) {
        try {
            List<CoPurchase> result = client.get()
                    .uri(uri -> uri.path("/internal/co-purchases")
                            .queryParam("productId", productId)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("co-purchase returned {} for productId={}", res.getStatusCode(), productId);
                        // swallow client errors — fall back to category candidates
                    })
                    .body(new ParameterizedTypeReference<List<CoPurchase>>() {});
            return result == null ? List.of() : result;
        } catch (Exception ex) {
            log.warn("co-purchase fetch failed for productId={}: {}", productId, ex.getMessage());
            return List.of();
        }
    }
}
