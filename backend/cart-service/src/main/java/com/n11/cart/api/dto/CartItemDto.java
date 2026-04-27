package com.n11.cart.api.dto;

import java.math.BigDecimal;

public record CartItemDto(
        Long id,
        Long productId,
        String productName,
        String imageUrl,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String currency
) {}
