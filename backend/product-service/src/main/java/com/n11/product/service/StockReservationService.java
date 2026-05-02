package com.n11.product.service;

import com.n11.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates atomic stock decrement / increment across a basket of items.
 *
 * <p>Reserve semantics: <em>all-or-nothing</em>.  If any item in the basket
 * fails its conditional decrement, the surrounding transaction rolls back
 * every prior decrement and the caller gets the list of insufficient ids
 * back — no partial reservation is left in the DB.</p>
 *
 * <p>Release is the saga compensation path: invoked when a downstream step
 * (payment, fraud check, …) fails after the reservation succeeded.  It
 * blindly increments by the recorded quantity; idempotency at the saga
 * level is handled by the order's status transition (a CANCELLED order
 * doesn't re-enter the cancellation pipeline).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockReservationService {

    private final ProductRepository repository;

    public record StockItem(Long productId, int quantity) {}

    public record ReserveResult(boolean ok, List<Long> insufficientProductIds) {
        public static ReserveResult success() { return new ReserveResult(true, List.of()); }
        public static ReserveResult insufficient(List<Long> ids) { return new ReserveResult(false, ids); }
    }

    /**
     * Decrements stock for every item in one transaction.  Returns success
     * only if all decrements landed; otherwise rolls back and reports which
     * product ids ran short so the client can render a precise error.
     *
     * <p>Why a runtime exception in the failure branch — couldn't we just
     * return the list?  Spring's {@link Transactional} only rolls back on
     * RuntimeException by default; returning early would commit the partial
     * decrements that did succeed.  Wrapping in a sentinel exception is the
     * cleanest way to abort the transaction while still surfacing the list
     * of offending ids.</p>
     */
    @Transactional
    public ReserveResult reserve(List<StockItem> items) {
        List<Long> insufficient = new ArrayList<>();
        for (StockItem item : items) {
            int affected = repository.decrementStockIfAvailable(item.productId(), item.quantity());
            if (affected == 0) {
                insufficient.add(item.productId());
            }
        }
        if (!insufficient.isEmpty()) {
            log.info("Reservation failed; insufficient stock for productIds={}", insufficient);
            // Throwing aborts the transaction so any successful decrements
            // are rolled back.  The caller catches and translates to the
            // ReserveResult.insufficient(...) response.
            throw new InsufficientStockException(insufficient);
        }
        log.info("Reserved stock for {} item(s)", items.size());
        return ReserveResult.success();
    }

    @Transactional
    public void release(List<StockItem> items) {
        for (StockItem item : items) {
            repository.incrementStock(item.productId(), item.quantity());
        }
        log.info("Released stock for {} item(s) (compensation)", items.size());
    }

    public static class InsufficientStockException extends RuntimeException {
        private final List<Long> productIds;

        public InsufficientStockException(List<Long> productIds) {
            super("insufficient stock for productIds=" + productIds);
            this.productIds = List.copyOf(productIds);
        }

        public List<Long> productIds() { return productIds; }
    }
}
