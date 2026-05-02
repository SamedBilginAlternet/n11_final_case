package com.n11.order.exception;

import java.util.List;

public class InsufficientStockException extends RuntimeException {
    private final List<Long> productIds;

    public InsufficientStockException(List<Long> productIds) {
        super("insufficient stock for productIds=" + productIds);
        this.productIds = List.copyOf(productIds);
    }

    public List<Long> productIds() { return productIds; }
}
