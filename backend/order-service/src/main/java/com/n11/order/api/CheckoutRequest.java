package com.n11.order.api;

import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull Long addressId
) {}
