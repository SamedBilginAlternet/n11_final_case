package com.n11.product.api.admin;

import java.util.List;

public record ProductMetricsDto(
        long totalProducts,
        long lowStockCount,
        int lowStockThreshold,
        List<LowStockItem> lowStock,
        List<CategoryShare> topCategories
) {
    public record LowStockItem(Long id, String name, String slug, int stock) {}

    public record CategoryShare(Long id, String name, long productCount) {}
}
