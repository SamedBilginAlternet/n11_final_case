package com.n11.cart.api.dto;

import com.n11.cart.domain.Coupon;
import com.n11.cart.domain.CouponType;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponDto(
        Long id,
        String code,
        String label,
        CouponType type,
        BigDecimal value,
        BigDecimal minCartTotal,
        Integer maxRedemptions,
        Integer redemptions,
        Instant validFrom,
        Instant validUntil,
        boolean active,
        Instant createdAt
) {
    public static CouponDto from(Coupon c) {
        return new CouponDto(
                c.getId(),
                c.getCode(),
                c.getLabel(),
                c.getType(),
                c.getValue(),
                c.getMinCartTotal(),
                c.getMaxRedemptions(),
                c.getRedemptions(),
                c.getValidFrom(),
                c.getValidUntil(),
                Boolean.TRUE.equals(c.getActive()),
                c.getCreatedAt()
        );
    }
}
