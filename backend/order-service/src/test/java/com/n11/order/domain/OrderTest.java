package com.n11.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void allowsPendingToAwaitingThenConfirmed() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.AWAITING_PAYMENT);
        order.transitionTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    void allowsCancellationFromAwaiting() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.AWAITING_PAYMENT);
        order.transitionTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void runsThroughFullFulfilmentLifecycle() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.AWAITING_PAYMENT);
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.PROCESSING);
        order.transitionTo(OrderStatus.SHIPPED);
        order.transitionTo(OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getProcessingAt()).isNotNull();
        assertThat(order.getShippedAt()).isNotNull();
        assertThat(order.getDeliveredAt()).isNotNull();
    }

    @Test
    void rejectsDeliveredToAnything() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.AWAITING_PAYMENT);
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.PROCESSING);
        order.transitionTo(OrderStatus.SHIPPED);
        order.transitionTo(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsCancelOnceShipped() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.AWAITING_PAYMENT);
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.PROCESSING);
        order.transitionTo(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsPendingDirectlyToConfirmed() {
        Order order = newOrder();
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CONFIRMED))
                .isInstanceOf(IllegalStateException.class);
    }

    private Order newOrder() {
        return Order.builder()
                .userId(1L).userEmail("u@n11.local").totalAmount(BigDecimal.TEN).currency("TRY")
                .status(OrderStatus.PENDING).build();
    }
}
