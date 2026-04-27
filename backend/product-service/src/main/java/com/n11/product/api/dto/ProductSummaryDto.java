package com.n11.product.api.dto;

import java.math.BigDecimal;

public record ProductSummaryDto(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        String currency,
        Integer stock,
        String imageUrl,
        Long categoryId,
        String categoryName
) {}
