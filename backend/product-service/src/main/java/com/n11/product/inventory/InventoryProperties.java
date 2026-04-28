package com.n11.product.inventory;

@org.springframework.boot.context.properties.ConfigurationProperties("n11.inventory.low-stock")
public record InventoryProperties(
        boolean enabled,
        int threshold,
        int maxItemsPerReport
) {
    public InventoryProperties {
        if (threshold <= 0) threshold = 5;
        if (maxItemsPerReport <= 0) maxItemsPerReport = 50;
    }
}
