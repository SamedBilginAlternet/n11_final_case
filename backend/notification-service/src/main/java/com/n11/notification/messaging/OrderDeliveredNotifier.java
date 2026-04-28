package com.n11.notification.messaging;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderDeliveredEvent;
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
public class OrderDeliveredNotifier {

    private final EmailService emailService;

    @RabbitListener(queues = SagaTopology.Queue.NOTIFICATION_ORDER_DELIVERED)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        if (event.correlationId() != null) MDC.put(CorrelationId.MDC_KEY, event.correlationId());
        try {
            log.info("OrderDelivered received orderId={}", event.orderId());
            emailService.sendOrderMail(
                    event.orderId(),
                    event.userId(),
                    event.userEmail(),
                    NotificationKind.ORDER_DELIVERED,
                    "Siparişin teslim edildi — n11 #" + event.orderId(),
                    "order-delivered",
                    Map.of("orderId", event.orderId()),
                    event.correlationId());
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
