package com.n11.cart.repository;

import com.n11.cart.domain.Coupon;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // Cached by uppercased code so 'KUPON100' and 'kupon100' share an entry.
    // Spring Cache auto-unwraps Optional return types (4.3+): SpEL `#result`
    // sees the unwrapped Coupon, never the Optional wrapper.  An earlier
    // version had `or !#result.isPresent()` here — that blew up with
    // SpelEvaluationException on every cache hit because Coupon has no
    // isPresent() method.  `#result == null` covers Optional.empty() (the
    // unwrap converts empty → null) and is sufficient on its own.
    @Cacheable(cacheNames = "coupons:byCode",
               key = "#code.toUpperCase()",
               unless = "#result == null")
    Optional<Coupon> findByCodeIgnoreCase(String code);

    /**
     * Atomic, race-safe redemption increment used by the saga reservation step.
     * Returns 1 when the row was incremented, 0 when the coupon is gone, inactive,
     * or already at max_redemptions.
     */
    @Modifying
    @CacheEvict(cacheNames = "coupons:byCode", key = "#code.toUpperCase()")
    @Query("""
            UPDATE Coupon c
               SET c.redemptions = c.redemptions + 1
             WHERE c.code = :code
               AND c.active = true
               AND (c.maxRedemptions IS NULL OR c.redemptions < c.maxRedemptions)
            """)
    int reserveOne(@Param("code") String code);

    /**
     * Compensation: only decrements when redemptions > 0 so duplicate
     * OrderCancelled deliveries can't drive the counter negative.
     */
    @Modifying
    @CacheEvict(cacheNames = "coupons:byCode", key = "#code.toUpperCase()")
    @Query("""
            UPDATE Coupon c
               SET c.redemptions = c.redemptions - 1
             WHERE c.code = :code
               AND c.redemptions > 0
            """)
    int releaseOne(@Param("code") String code);
}
