package com.n11.order.repository;

import com.n11.order.domain.Order;
import com.n11.order.domain.OrderStatus;
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

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    /**
     * Admin listing — every user's orders, optionally filtered by status.
     * Splits into two queries instead of using {@code (:status is null or ...)}
     * because PostgreSQL refuses to infer the type of a lone bind parameter
     * inside an {@code IS NULL} predicate ({@code could not determine data
     * type of parameter $1}).  Two trivial Spring Data derived queries
     * sidestep the issue entirely and keep the HQL out of the codebase.
     */
    default Page<Order> findAllByOptionalStatus(OrderStatus status, Pageable pageable) {
        return status == null
                ? findAllByOrderByCreatedAtDesc(pageable)
                : findByStatusOrderByCreatedAtDesc(status, pageable);
    }

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
