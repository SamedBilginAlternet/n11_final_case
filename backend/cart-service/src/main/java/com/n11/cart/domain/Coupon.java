package com.n11.cart.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "min_cart_total", precision = 12, scale = 2)
    private BigDecimal minCartTotal;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(nullable = false)
    private Integer redemptions;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.redemptions == null) this.redemptions = 0;
        if (this.active == null) this.active = true;
    }

    public boolean isValidAt(Instant instant) {
        if (!Boolean.TRUE.equals(active)) return false;
        if (validFrom != null && instant.isBefore(validFrom)) return false;
        if (validUntil != null && instant.isAfter(validUntil)) return false;
        if (maxRedemptions != null && redemptions != null && redemptions >= maxRedemptions) return false;
        return true;
    }
}
