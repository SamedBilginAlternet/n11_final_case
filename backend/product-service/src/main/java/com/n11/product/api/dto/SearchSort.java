package com.n11.product.api.dto;

/**
 * Canonical sort options for {@code /api/products}.  Names match the
 * URL query parameter values the frontend sends (e.g. {@code sort=price_asc}).
 */
public enum SearchSort {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    RATING,
    NEWEST;

    public static SearchSort from(String raw) {
        if (raw == null || raw.isBlank()) return RELEVANCE;
        try {
            return SearchSort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return RELEVANCE;
        }
    }
}
