package com.n11.cart.api.dto;

import com.n11.cart.pricing.DiscountKind;

import java.math.BigDecimal;

public record AppliedDiscountDto(
        String code,
        String label,
        DiscountKind kind,
        BigDecimal amount
) {}
