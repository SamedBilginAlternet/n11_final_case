package com.n11.cart.api.dto;

import com.n11.cart.domain.CouponType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin create/update payload for coupons.  Same shape both ways — POST
 * vs. PUT URL distinguishes intent.  Fields the admin can't set
 * (redemptions, createdAt) are intentionally omitted.
 */
public record CouponWriteRequest(
        @NotBlank @Size(min = 3, max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 160) String label,
        @NotNull CouponType type,
        @NotNull @DecimalMin(value = "0.01") BigDecimal value,
        @DecimalMin(value = "0.00") BigDecimal minCartTotal,
        @Positive Integer maxRedemptions,
        Instant validFrom,
        Instant validUntil,
        Boolean active
) {}
