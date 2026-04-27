package com.n11.order.service;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderCreatedEvent;
import com.n11.common.event.OrderItemPayload;
import com.n11.order.api.dto.OrderDto;
import com.n11.order.api.mapper.OrderMapper;
import com.n11.order.client.CartClient;
import com.n11.order.client.CartSnapshot;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderItem;
import com.n11.order.domain.OrderStatus;
import com.n11.order.messaging.OrderEventPublisher;
import com.n11.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final OrderRepository orderRepository;
    private final CartClient cartClient;
    private final OrderEventPublisher eventPublisher;
    private final OrderMapper mapper;

    @Transactional
    public OrderDto checkout(Long userId, String userEmail) {
        CartSnapshot cart = cartClient.fetchCurrent();
        String correlationId = MDC.get(CorrelationId.MDC_KEY);

        Order order = Order.builder()
                .userId(userId)
                .userEmail(userEmail)
                .status(OrderStatus.PENDING)
                .totalAmount(cart.totalAmount())
                .currency(cart.currency())
                .correlationId(correlationId)
                .build();

        cart.items().forEach(item -> order.addItem(OrderItem.builder()
                .productId(item.productId())
                .productName(item.productName())
                .quantity(item.quantity())
                .unitPrice(item.unitPrice())
                .build()));

        order.transitionTo(OrderStatus.AWAITING_PAYMENT);
        Order saved = orderRepository.save(order);
        log.info("Order created id={} userId={} total={} {}", saved.getId(), userId,
                saved.getTotalAmount(), saved.getCurrency());

        List<OrderItemPayload> payloadItems = saved.getItems().stream()
                .map(i -> new OrderItemPayload(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        OrderCreatedEvent event = OrderCreatedEvent.of(
                saved.getId(), userId, userEmail, saved.getTotalAmount(), saved.getCurrency(),
                payloadItems, correlationId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishOrderCreated(event);
            }
        });

        return mapper.toDto(saved);
    }
}
