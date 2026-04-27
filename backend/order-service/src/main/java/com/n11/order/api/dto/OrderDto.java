package com.n11.order.api.dto;

import com.n11.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
        Long id,
        Long userId,
        String userEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemDto> items,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {}
