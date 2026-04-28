package com.n11.order.service;

import com.n11.common.event.OrderDeliveredEvent;
import com.n11.common.event.OrderShippedEvent;
import com.n11.order.api.StatusUpdateRequest;
import com.n11.order.api.dto.OrderDto;
import com.n11.order.api.mapper.OrderMapper;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderStatus;
import com.n11.order.messaging.OrderEventPublisher;
import com.n11.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage of admin lifecycle transitions.
 *
 * <p>Each test activates Spring's TransactionSynchronizationManager
 * synchronizations, runs the service call, then manually fires
 * afterCommit on every registered hook to simulate a successful TX
 * boundary.  Without this, the publisher.publishOrder*() calls would
 * never fire because we're not inside a real Spring-managed transaction.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusServiceTest {

    @Mock OrderRepository repository;
    @Mock OrderMapper mapper;
    @Mock OrderEventPublisher publisher;

    @InjectMocks OrderStatusService service;

    @BeforeEach
    void enableSynchronizations() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @AfterEach
    void clearSynchronizations() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Order confirmedOrder() {
        Order o = Order.builder()
                .userId(42L)
                .userEmail("buyer@n11.com")
                .totalAmount(new BigDecimal("100.00"))
                .currency("TRY")
                .status(OrderStatus.CONFIRMED)
                .build();
        o.setId(7L);
        return o;
    }

    @Test
    void markProcessingDoesNotPublishEvent() {
        Order o = confirmedOrder();
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        when(repository.save(o)).thenReturn(o);
        when(mapper.toDto(o)).thenReturn(stubDto(o, OrderStatus.PROCESSING));

        service.markProcessing(7L);
        runAfterCommitHooks();

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(publisher, never()).publishOrderShipped(any());
        verify(publisher, never()).publishOrderDelivered(any());
    }

    @Test
    void markShippedPublishesShippedEventWithCarrierAndTracking() {
        Order o = confirmedOrder();
        o.setStatus(OrderStatus.PROCESSING);
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        when(repository.save(o)).thenReturn(o);
        when(mapper.toDto(o)).thenReturn(stubDto(o, OrderStatus.SHIPPED));

        service.markShipped(7L, new StatusUpdateRequest("PTT Kargo", "PTT-1234"));
        runAfterCommitHooks();

        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(o.getCarrier()).isEqualTo("PTT Kargo");
        assertThat(o.getTrackingNumber()).isEqualTo("PTT-1234");

        ArgumentCaptor<OrderShippedEvent> captor = ArgumentCaptor.forClass(OrderShippedEvent.class);
        verify(publisher).publishOrderShipped(captor.capture());
        OrderShippedEvent event = captor.getValue();
        assertThat(event.orderId()).isEqualTo(7L);
        assertThat(event.userEmail()).isEqualTo("buyer@n11.com");
        assertThat(event.carrier()).isEqualTo("PTT Kargo");
        assertThat(event.trackingNumber()).isEqualTo("PTT-1234");
    }

    @Test
    void markDeliveredPublishesDeliveredEvent() {
        Order o = confirmedOrder();
        o.setStatus(OrderStatus.SHIPPED);
        when(repository.findById(7L)).thenReturn(Optional.of(o));
        when(repository.save(o)).thenReturn(o);
        when(mapper.toDto(o)).thenReturn(stubDto(o, OrderStatus.DELIVERED));

        service.markDelivered(7L);
        runAfterCommitHooks();

        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        ArgumentCaptor<OrderDeliveredEvent> captor = ArgumentCaptor.forClass(OrderDeliveredEvent.class);
        verify(publisher).publishOrderDelivered(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(7L);
    }

    @Test
    void illegalStateTransitionMaps409() {
        Order o = confirmedOrder();
        o.setStatus(OrderStatus.DELIVERED); // can't transition out
        when(repository.findById(7L)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.markProcessing(7L))
                .isInstanceOf(ResponseStatusException.class);
        verify(publisher, never()).publishOrderShipped(any());
        verify(publisher, never()).publishOrderDelivered(any());
    }

    @Test
    void missingOrderThrows404() {
        when(repository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markProcessing(404L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order not found");
    }

    private void runAfterCommitHooks() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization s : syncs) s.afterCommit();
    }

    private OrderDto stubDto(Order o, OrderStatus status) {
        return new OrderDto(o.getId(), o.getUserId(), o.getUserEmail(), status,
                o.getTotalAmount(), o.getCurrency(), List.of(),
                null, null, null, null, null, null);
    }
}
