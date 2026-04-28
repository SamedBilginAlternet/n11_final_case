package com.n11.order.api.internal;

import com.n11.order.repository.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Cluster-internal endpoints for service-to-service calls.
 *
 * <p>NOT exposed via api-gateway — gateway only routes {@code /api/orders/**},
 * so {@code /internal/**} is reachable only from inside the docker network.
 * SecurityConfig permits this path because there is no end-user JWT to
 * validate; callers are other services.</p>
 *
 * <p>If we ever needed to harden this further, the cleanest knob is an
 * {@code X-Internal-Api-Key} shared secret + a request-matcher filter —
 * but for the bootcamp / single-droplet topology, network isolation is
 * the trust boundary we lean on.</p>
 */
@RestController
@RequestMapping("/internal/co-purchases")
@RequiredArgsConstructor
@Tag(name = "Internal — co-purchase signals")
public class InternalController {

    private static final int LOOKBACK_DAYS = 90;

    private final OrderRepository repository;

    @Operation(summary = "Top products bought together with the given product (last 90 days)")
    @GetMapping
    public List<CoPurchaseDto> coPurchases(@RequestParam Long productId,
                                           @RequestParam(defaultValue = "10") int limit) {
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        int capped = Math.min(Math.max(limit, 1), 50);
        return repository.findCoPurchaseCandidates(productId, since, PageRequest.of(0, capped))
                .stream()
                .map(row -> new CoPurchaseDto(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }
}
