package com.n11.product.recommendation;

import com.n11.product.api.dto.ProductSummaryDto;
import com.n11.product.api.mapper.ProductMapper;
import com.n11.product.domain.Product;
import com.n11.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the recommendation strip shown on the product detail page.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Pull co-purchase candidates from order-service (last 90 days).</li>
 *   <li>If fewer than {@code MIN_CANDIDATES}, top up with same-category
 *       top-rated products from local DB.</li>
 *   <li>If Groq is configured, send the candidate set + seed to llama-3.1-8b-instant
 *       for re-ranking + one-sentence Turkish "neden" explanations.</li>
 *   <li>If Groq is off or fails, return candidates in their original order
 *       with empty reasons — UI just shows the cards without subtitles.</li>
 * </ol>
 *
 * <p>Cached in Redis at {@code recommendations::<seedId>} for 5 minutes via
 * the existing Spring Cache config.  The whole flow costs roughly one
 * order-service hop + one Groq call per cache miss; everything else is
 * a Redis read.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private static final int CANDIDATE_POOL = 12;
    private static final int MIN_CANDIDATES = 6;
    private static final int RESULT_SIZE = 5;

    private final CoPurchaseClient coPurchaseClient;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ObjectProvider<GroqRecommendationClient> groqProvider;

    @Cacheable(value = "recommendations", key = "#seedId")
    public List<RecommendedItemDto> recommendFor(Long seedId) {
        Product seed = productRepository.findById(seedId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + seedId));

        Map<Long, Product> candidates = collectCandidates(seed);
        if (candidates.isEmpty()) return List.of();

        List<Product> ordered = new ArrayList<>(candidates.values());
        Map<Long, String> reasons = askGroqForReasons(seed, ordered);

        List<RecommendedItemDto> result = new ArrayList<>();
        // If Groq returned a ranking, honor that ID order; otherwise fall
        // back to the candidate order (co-purchase frequency first).
        Iterable<Long> orderedIds = reasons.isEmpty()
                ? ordered.stream().map(Product::getId).toList()
                : reasons.keySet();
        for (Long id : orderedIds) {
            Product p = candidates.get(id);
            if (p == null) continue; // hallucinated id from the LLM — drop it
            ProductSummaryDto summary = productMapper.toSummary(p);
            result.add(new RecommendedItemDto(summary, reasons.get(id)));
            if (result.size() >= RESULT_SIZE) break;
        }
        // If Groq picked fewer than RESULT_SIZE (or hallucinated), top up
        // from the candidate pool to keep the strip full.
        if (result.size() < RESULT_SIZE) {
            for (Product p : ordered) {
                if (result.stream().anyMatch(r -> r.product().id().equals(p.getId()))) continue;
                result.add(new RecommendedItemDto(productMapper.toSummary(p), null));
                if (result.size() >= RESULT_SIZE) break;
            }
        }
        return result;
    }

    private Map<Long, Product> collectCandidates(Product seed) {
        // LinkedHashMap to preserve insertion order = ranking order
        Map<Long, Product> byId = new LinkedHashMap<>();

        for (CoPurchaseClient.CoPurchase cp : coPurchaseClient.topCoPurchasesFor(seed.getId(), CANDIDATE_POOL)) {
            if (cp.productId().equals(seed.getId())) continue;
            productRepository.findById(cp.productId()).ifPresent(p -> byId.put(p.getId(), p));
        }

        if (byId.size() < MIN_CANDIDATES) {
            // Top up with same-category top-rated products until we hit the pool size
            for (Product p : productRepository.topRatedInCategory(
                    seed.getCategory().getId(), seed.getId(), PageRequest.of(0, CANDIDATE_POOL))) {
                byId.putIfAbsent(p.getId(), p);
                if (byId.size() >= CANDIDATE_POOL) break;
            }
        }
        return byId;
    }

    private Map<Long, String> askGroqForReasons(Product seed, List<Product> candidates) {
        GroqRecommendationClient groq = groqProvider.getIfAvailable();
        if (groq == null) return Map.of();
        List<GroqRecommendationClient.Ranked> ranked = groq.rerank(seed, candidates, RESULT_SIZE);
        if (ranked.isEmpty()) return Map.of();
        Map<Long, String> out = new LinkedHashMap<>();
        for (var r : ranked) out.put(r.productId(), r.reason());
        return out;
    }

}
