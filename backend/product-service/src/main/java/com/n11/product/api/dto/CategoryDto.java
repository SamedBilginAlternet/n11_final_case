package com.n11.product.api.dto;

public record CategoryDto(
        Long id,
        String name,
        String slug,
        String description
) {}
