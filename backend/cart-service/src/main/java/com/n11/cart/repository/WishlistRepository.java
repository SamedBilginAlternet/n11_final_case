package com.n11.cart.repository;

import com.n11.cart.domain.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.userId = :userId AND w.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
