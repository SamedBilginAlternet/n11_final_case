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
        String couponCode,
        String correlationId,
        // Carried only so payment-service can forward to the gateway. Never
        // persisted on either side. Nullable so older producers stay compatible
        // and so the mock gateway path still works without a card.
        CardData card
) {
    public static OrderCreatedEvent of(Long orderId,
                                       Long userId,
                                       String userEmail,
                                       BigDecimal totalAmount,
                                       String currency,
                                       List<OrderItemPayload> items,
                                       String couponCode,
                                       String correlationId,
                                       CardData card) {
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                orderId,
                userId,
                userEmail,
                totalAmount,
                currency,
                items,
                couponCode,
                correlationId,
                card
        );
    }

    public record CardData(
            String holderName,
            String number,
            String expireMonth,
            String expireYear,
            String cvc
    ) {}
}
