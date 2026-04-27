package com.n11.cart.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity
) {}
