package com.n11.order.domain;

public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
