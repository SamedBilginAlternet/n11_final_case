package com.n11.order.service;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderDeliveredEvent;
import com.n11.common.event.OrderProcessingEvent;
import com.n11.common.event.OrderShippedEvent;
import com.n11.order.api.StatusUpdateRequest;
import com.n11.order.api.dto.OrderDto;
import com.n11.order.api.mapper.OrderMapper;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderStatus;
import com.n11.order.messaging.OrderEventPublisher;
import com.n11.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-driven post-confirmation lifecycle transitions.
 *
 * <p>The state machine on {@link Order#transitionTo} already enforces what's
 * legal — this service just routes the verb to the right next state and
 * stamps any extra metadata (carrier, tracking number).</p>
 *
 * <p>SecurityConfig guards these endpoints with {@code hasRole("ADMIN")} so
 * only an admin can move an order through processing/shipped/delivered.</p>
 *
 * <p>PROCESSING + SHIPPED + DELIVERED transitions publish saga events for
 * notification-service to send hazırlanıyor + kargo + teslimat mailleri.
 * Publish is registered as an after-commit hook so a rolled-back transition
 * doesn't leak a phantom mail.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusService {

    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final OrderEventPublisher eventPublisher;

    @Transactional public OrderDto markProcessing(Long orderId) {
        return transition(orderId, OrderStatus.PROCESSING, null);
    }

    @Transactional public OrderDto markShipped(Long orderId, StatusUpdateRequest body) {
        return transition(orderId, OrderStatus.SHIPPED, body);
    }

    @Transactional public OrderDto markDelivered(Long orderId) {
        return transition(orderId, OrderStatus.DELIVERED, null);
    }

    private OrderDto transition(Long orderId, OrderStatus next, StatusUpdateRequest body) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        try {
            order.transitionTo(next);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
        if (next == OrderStatus.SHIPPED && body != null) {
            order.setCarrier(body.carrier());
            order.setTrackingNumber(body.trackingNumber());
        }
        Order saved = repository.save(order);
        log.info("Admin transitioned order id={} → {}", orderId, next);

        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (next == OrderStatus.PROCESSING) {
            OrderProcessingEvent event = OrderProcessingEvent.of(
                    saved.getId(), saved.getUserId(), saved.getUserEmail(), correlationId);
            registerAfterCommit(() -> eventPublisher.publishOrderProcessing(event));
        } else if (next == OrderStatus.SHIPPED) {
            OrderShippedEvent event = OrderShippedEvent.of(
                    saved.getId(), saved.getUserId(), saved.getUserEmail(),
                    saved.getCarrier(), saved.getTrackingNumber(), correlationId);
            registerAfterCommit(() -> eventPublisher.publishOrderShipped(event));
        } else if (next == OrderStatus.DELIVERED) {
            OrderDeliveredEvent event = OrderDeliveredEvent.of(
                    saved.getId(), saved.getUserId(), saved.getUserEmail(), correlationId);
            registerAfterCommit(() -> eventPublisher.publishOrderDelivered(event));
        }

        return mapper.toDto(saved);
    }

    private void registerAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
