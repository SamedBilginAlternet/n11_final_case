package com.n11.payment.service;

import com.n11.common.event.OrderCreatedEvent;
import com.n11.common.event.PaymentFailedEvent;
import com.n11.common.event.PaymentSucceededEvent;
import com.n11.payment.domain.Payment;
import com.n11.payment.domain.PaymentStatus;
import com.n11.payment.gateway.PaymentGateway;
import com.n11.payment.messaging.PaymentEventPublisher;
import com.n11.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher publisher;

    @Transactional
    public void process(OrderCreatedEvent order) {
        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(order.orderId())
                .userId(order.userId())
                .status(PaymentStatus.PENDING)
                .amount(order.totalAmount())
                .currency(order.currency())
                .correlationId(order.correlationId())
                .build());

        var command = new PaymentGateway.ChargeCommand(
                order.orderId(),
                order.userId(),
                order.userEmail(),
                order.totalAmount(),
                order.currency(),
                order.items()
        );

        var result = gateway.charge(command);

        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setProviderRef(result.providerRef());
            paymentRepository.save(payment);
            publisher.publishSucceeded(PaymentSucceededEvent.of(
                    order.orderId(), payment.getId(), result.providerRef(),
                    payment.getAmount(), payment.getCurrency(), order.correlationId()));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
            paymentRepository.save(payment);
            publisher.publishFailed(PaymentFailedEvent.of(
                    order.orderId(), payment.getId(), result.failureReason(), order.correlationId()));
        }
        log.info("Processed payment for orderId={} status={}", order.orderId(), payment.getStatus());
    }
}
