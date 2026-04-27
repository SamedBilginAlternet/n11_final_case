package com.n11.order.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "ix_orders_user", columnList = "user_id"),
        @Index(name = "ix_orders_status", columnList = "status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_email", nullable = false, length = 160)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "shipping_recipient", length = 120)
    private String shippingRecipient;

    @Column(name = "shipping_phone", length = 32)
    private String shippingPhone;

    @Column(name = "shipping_line1", length = 255)
    private String shippingLine1;

    @Column(name = "shipping_city", length = 80)
    private String shippingCity;

    @Column(name = "shipping_district", length = 80)
    private String shippingDistrict;

    @Column(name = "shipping_postal_code", length = 16)
    private String shippingPostalCode;

    @Column(name = "carrier", length = 60)
    private String carrier;

    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = OrderStatus.PENDING;
        if (this.currency == null) this.currency = "TRY";
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public void transitionTo(OrderStatus next) {
        if (!isAllowed(this.status, next)) {
            throw new IllegalStateException("Illegal transition: " + this.status + " → " + next);
        }
        this.status = next;
        Instant now = Instant.now();
        switch (next) {
            case CONFIRMED   -> { if (this.confirmedAt == null)  this.confirmedAt = now; }
            case PROCESSING  -> { if (this.processingAt == null) this.processingAt = now; }
            case SHIPPED     -> { if (this.shippedAt == null)    this.shippedAt = now; }
            case DELIVERED   -> { if (this.deliveredAt == null)  this.deliveredAt = now; }
            case CANCELLED   -> { if (this.cancelledAt == null)  this.cancelledAt = now; }
            default -> { /* PENDING / AWAITING_PAYMENT have no dedicated stamp */ }
        }
    }

    private static boolean isAllowed(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PENDING          -> to == OrderStatus.AWAITING_PAYMENT || to == OrderStatus.CANCELLED;
            case AWAITING_PAYMENT -> to == OrderStatus.CONFIRMED        || to == OrderStatus.CANCELLED;
            // After payment, the order can be cancelled until it actually ships.
            case CONFIRMED        -> to == OrderStatus.PROCESSING       || to == OrderStatus.CANCELLED;
            case PROCESSING       -> to == OrderStatus.SHIPPED          || to == OrderStatus.CANCELLED;
            case SHIPPED          -> to == OrderStatus.DELIVERED;
            default -> false;
        };
    }
}
