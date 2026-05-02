package com.n11.order.service;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderCreatedEvent;
import com.n11.common.event.OrderItemPayload;
import com.n11.order.api.CheckoutRequest;
import com.n11.order.api.dto.OrderDto;
import com.n11.order.api.mapper.OrderMapper;
import com.n11.order.client.AddressClient;
import com.n11.order.client.AddressSnapshot;
import com.n11.order.client.CartClient;
import com.n11.order.client.CartSnapshot;
import com.n11.order.client.ProductStockClient;
import com.n11.order.client.ProductStockClient.ReservationItem;
import com.n11.order.client.ProductStockClient.ReservationResponse;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderItem;
import com.n11.order.domain.OrderStatus;
import com.n11.order.exception.InsufficientStockException;
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
    private final AddressClient addressClient;
    private final ProductStockClient productStockClient;
    private final OrderEventPublisher eventPublisher;
    private final OrderMapper mapper;

    @Transactional
    public OrderDto checkout(Long userId, String userEmail, Long addressId, CheckoutRequest.CardDetails card) {
        AddressSnapshot address = addressClient.fetch(addressId);
        CartSnapshot cart = cartClient.fetchCurrent();
        String correlationId = MDC.get(CorrelationId.MDC_KEY);

        // Reserve stock atomically before persisting the order — if any line
        // is short, product-service rolls back its decrements and we surface
        // the offending ids without ever creating an order.  Synchronous on
        // purpose: the user must see "X is out of stock" immediately, not
        // after a saga round-trip.
        List<ReservationItem> reservationItems = cart.items().stream()
                .map(i -> new ReservationItem(i.productId(), i.quantity()))
                .toList();
        ReservationResponse reservation = productStockClient.reserve(reservationItems);
        if (!reservation.ok()) {
            log.info("Checkout rejected — insufficient stock for productIds={}",
                    reservation.insufficientProductIds());
            throw new InsufficientStockException(reservation.insufficientProductIds());
        }

        // If anything between here and commit fails, the order won't exist
        // but the stock has already been decremented.  Hook a rollback-only
        // compensation so the leak heals automatically — the saga path
        // (OrderCancelled event after payment fail) covers post-commit
        // failures, this hook covers pre-commit ones.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    log.warn("Order transaction rolled back after stock reserve, releasing stock");
                    try {
                        productStockClient.release(reservationItems);
                    } catch (Exception ex) {
                        log.error("Failed to release stock after order rollback — manual cleanup needed", ex);
                    }
                }
            }
        });

        Order order = Order.builder()
                .userId(userId)
                .userEmail(userEmail)
                .status(OrderStatus.PENDING)
                .totalAmount(cart.totalAmount())
                .currency(cart.currency())
                .correlationId(correlationId)
                .couponCode(cart.couponCode())
                .shippingRecipient(address.recipientName())
                .shippingPhone(address.phone())
                .shippingLine1(address.line1())
                .shippingCity(address.city())
                .shippingDistrict(address.district())
                .shippingPostalCode(address.postalCode())
                .build();

        cart.items().forEach(item -> order.addItem(OrderItem.builder()
                .productId(item.productId())
                .productName(item.productName())
                .quantity(item.quantity())
                .unitPrice(item.unitPrice())
                .build()));

        order.transitionTo(OrderStatus.AWAITING_PAYMENT);
        Order saved = orderRepository.save(order);
        log.info("Order created id={} userId={} total={} {} addressId={}",
                saved.getId(), userId, saved.getTotalAmount(), saved.getCurrency(), addressId);

        List<OrderItemPayload> payloadItems = saved.getItems().stream()
                .map(i -> new OrderItemPayload(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        OrderCreatedEvent.CardData cardPayload = card == null ? null : new OrderCreatedEvent.CardData(
                card.holderName(), card.number(), card.expireMonth(), card.expireYear(), card.cvc());
        OrderCreatedEvent.BuyerData buyerPayload = new OrderCreatedEvent.BuyerData(
                address.recipientName(), address.phone(), address.line1(),
                address.city(), address.district(), address.postalCode());
        OrderCreatedEvent event = OrderCreatedEvent.of(
                saved.getId(), userId, userEmail, saved.getTotalAmount(), saved.getCurrency(),
                payloadItems, saved.getCouponCode(), correlationId, cardPayload, buyerPayload);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishOrderCreated(event);
            }
        });

        return mapper.toDto(saved);
    }
}
