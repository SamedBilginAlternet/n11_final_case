package com.n11.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long userId,
        String userEmail,
        String reason,
        String couponCode,
        // Items the order reserved — product-service consumes the event and
        // increments stock back, mirroring the decrement done at OrderCreated
        // time.  Missing/empty list means the cancellation is from a state
        // where stock was never reserved (rare, but kept null-safe).
        List<OrderItemPayload> items,
        String correlationId
) {
    public static OrderCancelledEvent of(Long orderId,
                                         Long userId,
                                         String userEmail,
                                         String reason,
                                         String couponCode,
                                         List<OrderItemPayload> items,
                                         String correlationId) {
        return new OrderCancelledEvent(
                UUID.randomUUID(), Instant.now(), orderId, userId, userEmail, reason,
                couponCode, items == null ? List.of() : List.copyOf(items), correlationId);
    }
}
