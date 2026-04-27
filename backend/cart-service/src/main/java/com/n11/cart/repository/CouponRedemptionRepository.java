package com.n11.cart.repository;

import com.n11.cart.domain.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    Optional<CouponRedemption> findByCouponIdAndOrderId(Long couponId, Long orderId);

    Optional<CouponRedemption> findByOrderId(Long orderId);
}
