package com.n11.cart.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyCouponRequest(
        @NotBlank @Size(min = 3, max = 40) String code
) {}
