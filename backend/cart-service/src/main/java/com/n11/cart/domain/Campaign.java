package com.n11.cart.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 160)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CampaignType type;

    @Column(nullable = false)
    private Integer priority;

    @Column(precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "pay_y")
    private Integer payY;

    @Column(name = "min_cart_total", precision = 12, scale = 2)
    private BigDecimal minCartTotal;

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
        if (this.priority == null) this.priority = 100;
        if (this.active == null) this.active = true;
    }
}
