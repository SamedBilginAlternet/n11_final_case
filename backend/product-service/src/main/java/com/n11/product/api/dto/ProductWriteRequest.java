package com.n11.product.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Admin create/update payload.  Same shape for both — backend infers
 * "create" vs. "update" from the URL (POST vs. PUT).  Fields the admin
 * can't set (id, ratings, timestamps) are intentionally omitted.
 */
public record ProductWriteRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 220) String slug,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin(value = "0.00") BigDecimal price,
        @Size(min = 3, max = 3) String currency,
        @NotNull @PositiveOrZero Integer stock,
        @Size(max = 500) String imageUrl,
        @NotNull Long categoryId
) {}
