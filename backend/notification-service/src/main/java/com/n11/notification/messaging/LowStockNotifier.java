package com.n11.notification.messaging;

import com.n11.common.event.LowStockReportEvent;
import com.n11.common.saga.SagaTopology;
import com.n11.notification.config.NotificationProperties;
import com.n11.notification.service.LowStockMailer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LowStockNotifier {

    private final LowStockMailer mailer;
    private final NotificationProperties properties;

    @RabbitListener(queues = SagaTopology.Queue.NOTIFICATION_LOW_STOCK)
    public void onLowStockReport(LowStockReportEvent event) {
        if (event.items() == null || event.items().isEmpty()) return;
        String recipient = properties.adminAlertRecipient();
        if (recipient == null || recipient.isBlank()) {
            log.warn("Low-stock report received with {} items but no admin recipient configured "
                    + "(n11.notification.admin-alert-recipient) — skipping send", event.items().size());
            return;
        }
        log.info("Sending low-stock alert to {} ({} items, threshold={})",
                recipient, event.items().size(), event.threshold());
        mailer.send(recipient, event);
    }
}
