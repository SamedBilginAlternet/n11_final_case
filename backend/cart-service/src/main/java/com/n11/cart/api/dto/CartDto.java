package com.n11.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(
        Long id,
        Long userId,
        List<CartItemDto> items,
        BigDecimal totalAmount,
        String currency,
        Integer totalQuantity
) {}
