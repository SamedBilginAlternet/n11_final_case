package com.n11.product.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductDetailDto(
        Long id,
        String name,
        String slug,
        String description,
        BigDecimal price,
        String currency,
        Integer stock,
        String imageUrl,
        Long categoryId,
        String categoryName,
        String categorySlug,
        Instant createdAt,
        Instant updatedAt
) {}
