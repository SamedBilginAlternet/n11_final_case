package com.n11.order.api;

import jakarta.validation.constraints.Size;

/**
 * Body for admin status-transition endpoints. Carrier+trackingNumber only
 * meaningful when transitioning to SHIPPED, otherwise ignored.
 */
public record StatusUpdateRequest(
        @Size(max = 60)  String carrier,
        @Size(max = 80)  String trackingNumber
) {}
