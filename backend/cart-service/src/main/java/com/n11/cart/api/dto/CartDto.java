package com.n11.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cart receipt as the frontend renders it.
 *
 * <pre>
 *   subtotal       Σ unit_price × qty               (before discounts)
 *   discounts      one row per applied campaign / coupon
 *   totalDiscount  Σ discounts.amount
 *   totalAmount    subtotal − totalDiscount, floored at 0  (final number)
 * </pre>
 */
public record CartDto(
        Long id,
        Long userId,
        List<CartItemDto> items,
        BigDecimal subtotal,
        List<AppliedDiscountDto> discounts,
        BigDecimal totalDiscount,
        BigDecimal totalAmount,
        String currency,
        Integer totalQuantity,
        String couponCode
) {}
