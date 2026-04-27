package com.n11.cart.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSnapshot(
        Long id,
        String slug,
        String name,
        String imageUrl,
        BigDecimal price,
        String currency,
        Integer stock
) {}
