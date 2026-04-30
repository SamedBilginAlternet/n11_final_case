package com.n11.payment.service;

import com.n11.common.event.OrderCreatedEvent;
import com.n11.common.event.OrderItemPayload;
import com.n11.common.event.PaymentFailedEvent;
import com.n11.common.event.PaymentSucceededEvent;
import com.n11.payment.domain.Payment;
import com.n11.payment.domain.PaymentStatus;
import com.n11.payment.gateway.PaymentGateway;
import com.n11.payment.messaging.PaymentEventPublisher;
import com.n11.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentGateway gateway;
    @Mock PaymentRepository repository;
    @Mock PaymentEventPublisher publisher;

    @InjectMocks PaymentService service;

    private OrderCreatedEvent sampleEvent() {
        return OrderCreatedEvent.of(7L, 1L, "u@n11.local",
                new BigDecimal("100"), "TRY",
                List.of(new OrderItemPayload(1L, "X", 1, new BigDecimal("100"))),
                null,
                "cid",
                null);
    }

    @Test
    void persistsAndPublishesSuccess() {
        when(repository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(33L);
            return p;
        });
        when(gateway.charge(any())).thenReturn(PaymentGateway.PaymentChargeResult.success("REF-1"));

        service.process(sampleEvent());

        ArgumentCaptor<PaymentSucceededEvent> captor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(publisher).publishSucceeded(captor.capture());
        assertThat(captor.getValue().providerRef()).isEqualTo("REF-1");
        assertThat(captor.getValue().orderId()).isEqualTo(7L);
    }

    @Test
    void persistsAndPublishesFailure() {
        when(repository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(34L);
            return p;
        });
        when(gateway.charge(any())).thenReturn(PaymentGateway.PaymentChargeResult.failure("declined"));

        service.process(sampleEvent());

        ArgumentCaptor<Payment> persistCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(repository, atLeastOnce()).save(persistCaptor.capture());
        Payment last = persistCaptor.getValue();
        assertThat(last.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(last.getFailureReason()).isEqualTo("declined");

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(publisher).publishFailed(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("declined");
    }
}
