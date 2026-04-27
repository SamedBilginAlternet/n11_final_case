package com.n11.product.api.dto;

import java.time.Instant;

public record ReviewDto(
        Long id,
        Long productId,
        Long userId,
        String userName,
        int rating,
        String body,
        Instant createdAt,
        Instant updatedAt
) {}
