package com.n11.product.api.dto;

public record AutocompleteSuggestion(
        Long id,
        String name,
        String slug,
        String imageUrl,
        String categoryName
) {}
