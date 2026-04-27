package com.n11.order.messaging;

import com.n11.common.event.OrderCancelledEvent;
import com.n11.common.event.OrderConfirmedEvent;
import com.n11.common.event.PaymentFailedEvent;
import com.n11.common.event.PaymentSucceededEvent;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderStatus;
import com.n11.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentResultListenerTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderEventPublisher publisher;

    @InjectMocks PaymentResultListener listener;

    @Test
    void confirmsOrderOnSuccessAndPublishesConfirmed() {
        Order order = newOrder(7L, OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        listener.onPaymentSucceeded(PaymentSucceededEvent.of(7L, 33L, "iyz-1", new BigDecimal("100"), "TRY", "cid"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        ArgumentCaptor<OrderConfirmedEvent> captor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(publisher).publishOrderConfirmed(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(7L);
    }

    @Test
    void cancelsOrderOnFailureAndPublishesCancelled() {
        Order order = newOrder(8L, OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));

        listener.onPaymentFailed(PaymentFailedEvent.of(8L, 44L, "card_declined", "cid"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailureReason()).isEqualTo("card_declined");
        ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(publisher).publishOrderCancelled(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(8L);
        assertThat(captor.getValue().reason()).isEqualTo("card_declined");
    }

    @Test
    void ignoresDuplicateSuccessOnAlreadyConfirmedOrder() {
        Order order = newOrder(7L, OrderStatus.AWAITING_PAYMENT);
        order.transitionTo(OrderStatus.CONFIRMED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        listener.onPaymentSucceeded(PaymentSucceededEvent.of(7L, 33L, "iyz-1", new BigDecimal("100"), "TRY", "cid"));

        verify(publisher, never()).publishOrderConfirmed(any());
    }

    @Test
    void noopWhenOrderUnknown() {
        when(orderRepository.findById(anyLong())).thenReturn(Optional.empty());

        listener.onPaymentSucceeded(PaymentSucceededEvent.of(99L, 1L, "x", BigDecimal.ONE, "TRY", "cid"));

        verifyNoInteractions(publisher);
    }

    private Order newOrder(Long id, OrderStatus status) {
        Order o = Order.builder().id(id).userId(1L).userEmail("u@n11.local")
                .totalAmount(BigDecimal.TEN).currency("TRY")
                .status(OrderStatus.PENDING).build();
        if (status == OrderStatus.AWAITING_PAYMENT) o.transitionTo(OrderStatus.AWAITING_PAYMENT);
        return o;
    }
}
