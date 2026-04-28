package com.n11.product.recommendation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products/{id}/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(summary = "Top 5 products related to the given product, with one-sentence Turkish AI explanation per item")
    @GetMapping
    public List<RecommendedItemDto> recommendations(@PathVariable Long id) {
        return recommendationService.recommendFor(id);
    }
}
