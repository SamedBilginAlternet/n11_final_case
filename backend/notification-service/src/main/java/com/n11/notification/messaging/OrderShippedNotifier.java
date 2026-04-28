package com.n11.notification.messaging;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderShippedEvent;
import com.n11.common.saga.SagaTopology;
import com.n11.notification.domain.NotificationKind;
import com.n11.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderShippedNotifier {

    private final EmailService emailService;

    @RabbitListener(queues = SagaTopology.Queue.NOTIFICATION_ORDER_SHIPPED)
    public void onOrderShipped(OrderShippedEvent event) {
        if (event.correlationId() != null) MDC.put(CorrelationId.MDC_KEY, event.correlationId());
        try {
            log.info("OrderShipped received orderId={} carrier={} tracking={}",
                    event.orderId(), event.carrier(), event.trackingNumber());
            Map<String, Object> model = new HashMap<>();
            model.put("orderId", event.orderId());
            model.put("carrier", event.carrier());
            model.put("trackingNumber", event.trackingNumber());
            emailService.sendOrderMail(
                    event.orderId(),
                    event.userId(),
                    event.userEmail(),
                    NotificationKind.ORDER_SHIPPED,
                    "Siparişin kargoya verildi — n11 #" + event.orderId(),
                    "order-shipped",
                    model,
                    event.correlationId());
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
