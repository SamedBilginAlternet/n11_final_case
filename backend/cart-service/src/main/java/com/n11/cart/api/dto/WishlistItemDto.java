package com.n11.cart.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WishlistItemDto(
        Long productId,
        String slug,
        String name,
        String imageUrl,
        BigDecimal price,
        String currency,
        Integer stock,
        Instant addedAt
) {}
