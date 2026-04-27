package com.n11.payment.api.dto;

import com.n11.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDto(
        Long id,
        Long orderId,
        Long userId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String providerRef,
        String failureReason,
        Integer attempt,
        Instant createdAt,
        Instant updatedAt
) {}
