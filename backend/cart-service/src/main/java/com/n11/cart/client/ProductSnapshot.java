package com.n11.cart.client;

import java.math.BigDecimal;

public record ProductSnapshot(
        Long id,
        String name,
        String imageUrl,
        BigDecimal price,
        String currency,
        Integer stock
) {}
