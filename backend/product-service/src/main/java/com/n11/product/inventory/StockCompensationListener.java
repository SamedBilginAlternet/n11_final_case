package com.n11.product.inventory;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderCancelledEvent;
import com.n11.common.saga.SagaTopology;
import com.n11.product.service.StockReservationService;
import com.n11.product.service.StockReservationService.StockItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Saga compensation — when an order is cancelled (typically because payment
 * failed), restore the stock that was reserved at checkout.  The cancelled
 * event carries the items so we don't need to call back into order-service.
 *
 * <p>Idempotency: the order's state machine forbids re-entering CANCELLED,
 * so order-service won't republish a duplicate event for the same order.
 * If the broker redelivers the same event (consumer crash before ack), the
 * second increment would over-restore — but since orders are not re-tried
 * once CANCELLED, the upstream guarantees only one cancellation event per
 * order.  Accepting at-most-once compensation here is fine.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockCompensationListener {

    private final StockReservationService stockService;

    @RabbitListener(queues = SagaTopology.Queue.PRODUCT_ORDER_CANCELLED_STOCK)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (event.correlationId() != null) MDC.put(CorrelationId.MDC_KEY, event.correlationId());
        try {
            if (event.items() == null || event.items().isEmpty()) {
                log.info("OrderCancelled for orderId={} carries no items — nothing to release",
                        event.orderId());
                return;
            }
            List<StockItem> items = event.items().stream()
                    .map(i -> new StockItem(i.productId(), i.quantity()))
                    .toList();
            stockService.release(items);
            log.info("Compensated stock for cancelled orderId={} ({} items)",
                    event.orderId(), items.size());
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
