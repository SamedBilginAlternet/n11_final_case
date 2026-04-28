package com.n11.product.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sidebar facet data — what counts to show next to each filter option,
 * plus the price range present in the *current* result set so the slider
 * snaps to actual data instead of a hard-coded 0..max.
 */
public record SearchFacetsDto(
        List<CategoryFacet> categories,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        long totalMatches
) {
    public record CategoryFacet(Long id, String name, long count) {}
}
