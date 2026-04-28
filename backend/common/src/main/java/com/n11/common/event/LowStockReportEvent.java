package com.n11.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LowStockReportEvent(
        UUID eventId,
        Instant occurredAt,
        int threshold,
        List<Item> items
) {
    public record Item(Long productId, String name, String slug, int stock) {}

    public static LowStockReportEvent of(int threshold, List<Item> items) {
        return new LowStockReportEvent(UUID.randomUUID(), Instant.now(), threshold, items);
    }
}
