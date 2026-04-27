package com.n11.product.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products",
        uniqueConstraints = @UniqueConstraint(columnNames = "slug"),
        indexes = {
                @Index(name = "ix_products_category", columnList = "category_id"),
                @Index(name = "ix_products_name", columnList = "name")
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 220)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "rating_average", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAverage;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.currency == null) this.currency = "TRY";
        if (this.stock == null) this.stock = 0;
        if (this.ratingAverage == null) this.ratingAverage = BigDecimal.ZERO;
        if (this.ratingCount == null) this.ratingCount = 0;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
