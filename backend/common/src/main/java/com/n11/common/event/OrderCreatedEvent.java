package com.n11.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long userId,
        String userEmail,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemPayload> items,
        String correlationId
) {
    public static OrderCreatedEvent of(Long orderId,
                                       Long userId,
                                       String userEmail,
                                       BigDecimal totalAmount,
                                       String currency,
                                       List<OrderItemPayload> items,
                                       String correlationId) {
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                orderId,
                userId,
                userEmail,
                totalAmount,
                currency,
                items,
                correlationId
        );
    }
}
