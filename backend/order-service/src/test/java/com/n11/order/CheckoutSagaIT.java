package com.n11.order;

import com.n11.common.event.OrderConfirmedEvent;
import com.n11.common.event.PaymentFailedEvent;
import com.n11.common.event.PaymentSucceededEvent;
import com.n11.common.saga.SagaTopology;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderStatus;
import com.n11.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

/**
 * Integration test for the order-service half of the checkout choreography.
 *
 * Boots the real Spring context against a Testcontainers Postgres + RabbitMQ
 * pair, drops a {@link PaymentSucceededEvent} (or failure) onto the saga
 * exchange, then waits for the consequences to land both in the database
 * (status transition) and back on the exchange (downstream event).
 *
 * The other half — payment-service consuming order.created and producing
 * payment.* — is exercised by payment-service's own tests; cross-service
 * end-to-end is left to manual smoke tests on the deployed stack so we
 * don't need to spin two Spring contexts in CI.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(CheckoutSagaIT.SpyConfig.class)
class CheckoutSagaIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderdb").withUsername("test").withPassword("test");

    @Container @ServiceConnection
    static RabbitMQContainer rabbitContainer = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired OrderRepository orderRepository;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired RabbitAdmin admin;

    @BeforeEach
    void cleanState() {
        orderRepository.deleteAll();
        admin.purgeQueue("test.order-confirmed.q", false);
        admin.purgeQueue("test.order-cancelled.q", false);
    }

    @Test
    void paymentSucceeded_flipsOrderToConfirmed_andEmitsOrderConfirmed() {
        Order seed = saveSeedOrder(OrderStatus.AWAITING_PAYMENT, "+905551234567");

        // Drop the message a real payment-service would publish on a successful
        // Iyzico authorisation.  Event carries correlationId so MDC propagation
        // surfaces in the listener's logs — not asserted here, just exercised.
        PaymentSucceededEvent event = PaymentSucceededEvent.of(
                seed.getId(), 999L, "iyz-test-1", new BigDecimal("199.90"), "TRY", "corr-it-1");
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE,
                SagaTopology.RoutingKey.PAYMENT_SUCCEEDED, event);

        // Listener runs on a worker thread; poll the DB until the transition
        // lands.  5s is generous — local Testcontainers usually clears in <1s.
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            Order reloaded = orderRepository.findById(seed.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(reloaded.getConfirmedAt()).isNotNull();
        });

        // Spy queue captures order.confirmed re-publish; verifying it actually
        // lands on the exchange (not just in the DB) is what makes this a
        // saga test rather than just a state-machine test.
        OrderConfirmedEvent published = (OrderConfirmedEvent) rabbitTemplate.receiveAndConvert(
                "test.order-confirmed.q", 5000);
        assertThat(published).isNotNull();
        assertThat(published.orderId()).isEqualTo(seed.getId());
    }

    @Test
    void paymentFailed_flipsOrderToCancelled_andEmitsOrderCancelled() {
        Order seed = saveSeedOrder(OrderStatus.AWAITING_PAYMENT, "+905559876543");

        PaymentFailedEvent event = PaymentFailedEvent.of(
                seed.getId(), 1000L, "card_declined", "corr-it-2");
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE,
                SagaTopology.RoutingKey.PAYMENT_FAILED, event);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            Order reloaded = orderRepository.findById(seed.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(reloaded.getFailureReason()).isEqualTo("card_declined");
            assertThat(reloaded.getCancelledAt()).isNotNull();
        });

        Object published = rabbitTemplate.receiveAndConvert("test.order-cancelled.q", 5000);
        assertThat(published).isNotNull();
    }

    private Order saveSeedOrder(OrderStatus status, String phone) {
        Order o = Order.builder()
                .userId(7L)
                .userEmail("integration@n11.local")
                .status(status)
                .totalAmount(new BigDecimal("199.90"))
                .currency("TRY")
                .shippingRecipient("Integration Test")
                .shippingPhone(phone)
                .shippingLine1("Test Mh.")
                .shippingCity("Istanbul")
                .build();
        return orderRepository.save(o);
    }

    /**
     * Spy queues bound to order.confirmed / order.cancelled routing keys
     * so the test can assert on actual exchange traffic without depending
     * on cart-service or notification-service queues being declared.
     */
    @TestConfiguration
    static class SpyConfig {
        @Bean
        Queue testOrderConfirmedQueue() {
            return QueueBuilder.nonDurable("test.order-confirmed.q").autoDelete().build();
        }

        @Bean
        Queue testOrderCancelledQueue() {
            return QueueBuilder.nonDurable("test.order-cancelled.q").autoDelete().build();
        }

        @Bean
        Binding bindTestOrderConfirmed(Queue testOrderConfirmedQueue, TopicExchange sagaExchange) {
            return BindingBuilder.bind(testOrderConfirmedQueue).to(sagaExchange)
                    .with(SagaTopology.RoutingKey.ORDER_CONFIRMED);
        }

        @Bean
        Binding bindTestOrderCancelled(Queue testOrderCancelledQueue, TopicExchange sagaExchange) {
            return BindingBuilder.bind(testOrderCancelledQueue).to(sagaExchange)
                    .with(SagaTopology.RoutingKey.ORDER_CANCELLED);
        }
    }
}
