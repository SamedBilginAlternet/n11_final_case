package com.n11.notification.messaging;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderConfirmedEvent;
import com.n11.common.saga.SagaTopology;
import com.n11.notification.domain.NotificationKind;
import com.n11.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmedNotifier {

    private final EmailService emailService;

    @RabbitListener(queues = SagaTopology.Queue.NOTIFICATION_ORDER_CONFIRMED)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        if (event.correlationId() != null) MDC.put(CorrelationId.MDC_KEY, event.correlationId());
        try {
            log.info("OrderConfirmed received orderId={}", event.orderId());
            emailService.sendOrderMail(
                    event.orderId(),
                    event.userId(),
                    event.userEmail(),
                    NotificationKind.ORDER_CONFIRMED,
                    "Siparişin onaylandı — n11 #" + event.orderId(),
                    "order-confirmed",
                    Map.of("orderId", event.orderId()),
                    event.correlationId());
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
