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
        ShippingDto shipping,
        TrackingDto tracking,
        TimelineDto timeline,
        Instant createdAt,
        Instant updatedAt
) {
    public record ShippingDto(
            String recipient,
            String phone,
            String line1,
            String city,
            String district,
            String postalCode
    ) {}

    public record TrackingDto(
            String carrier,
            String trackingNumber
    ) {}

    public record TimelineDto(
            Instant placedAt,
            Instant confirmedAt,
            Instant processingAt,
            Instant shippedAt,
            Instant deliveredAt,
            Instant cancelledAt
    ) {}
}
