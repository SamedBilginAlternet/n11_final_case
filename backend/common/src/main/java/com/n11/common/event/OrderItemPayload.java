package com.n11.common.event;

import java.math.BigDecimal;

public record OrderItemPayload(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice
) {}
