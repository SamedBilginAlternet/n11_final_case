package com.n11.order.repository;

import com.n11.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    /**
     * Co-purchase signal — products that ended up in the same order as
     * {@code productId} most often, within the time window.  Self-matches
     * are excluded; cancelled orders are excluded so we don't recommend
     * something a customer abandoned.
     *
     * <p>Returns rows of [productId, productName, occurrences] sorted by
     * occurrences desc.  product-service treats this as a candidate set
     * and re-ranks/explains via the LLM downstream.</p>
     */
    @Query("""
            select co.productId, co.productName, count(co) as cnt
            from OrderItem self
            join self.order o
            join o.items co
            where self.productId = :productId
              and co.productId <> :productId
              and o.status <> com.n11.order.domain.OrderStatus.CANCELLED
              and o.createdAt >= :since
            group by co.productId, co.productName
            order by cnt desc
            """)
    List<Object[]> findCoPurchaseCandidates(@Param("productId") Long productId,
                                            @Param("since") Instant since,
                                            Pageable pageable);
}
