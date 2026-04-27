package com.n11.order.service;

import com.n11.order.api.StatusUpdateRequest;
import com.n11.order.api.dto.OrderDto;
import com.n11.order.api.mapper.OrderMapper;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderStatus;
import com.n11.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusService {

    private final OrderRepository repository;
    private final OrderMapper mapper;

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
        log.info("Admin transitioned order id={} → {}", orderId, next);
        return mapper.toDto(repository.save(order));
    }
}
