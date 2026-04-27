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
        BigDecimal ratingAverage,
        Integer ratingCount,
        Long categoryId,
        String categoryName
) {}
