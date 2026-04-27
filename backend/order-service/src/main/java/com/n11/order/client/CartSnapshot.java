package com.n11.order.client;

import java.math.BigDecimal;
import java.util.List;

public record CartSnapshot(
        Long id,
        Long userId,
        List<CartItem> items,
        BigDecimal subtotal,
        BigDecimal totalDiscount,
        BigDecimal totalAmount,
        String currency,
        Integer totalQuantity,
        String couponCode
) {
    public record CartItem(
            Long id,
            Long productId,
            String productName,
            String imageUrl,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String currency
    ) {}
}
