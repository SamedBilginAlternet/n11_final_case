package com.n11.product.inventory;

import com.n11.common.event.LowStockReportEvent;
import com.n11.common.saga.SagaTopology;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Daily critical-stock scanner.
 *
 * <p>Once per scheduled tick (cron in {@code n11.inventory.low-stock.cron}),
 * scans products whose stock is at or below the configured threshold and
 * publishes a {@link LowStockReportEvent}.  notification-service picks
 * the event up and emails an admin recipient.</p>
 *
 * <p>If no products are below threshold the scanner skips publishing —
 * we don't want a daily "everything is fine" email cluttering the
 * admin inbox.  The scanner logs a debug line in that case so it's
 * still observable in metrics that the run executed.</p>
 *
 * <p>Disabled by default ({@code enabled: false}); flip the env to
 * activate per environment without code changes.</p>
 */
@Component
@ConditionalOnProperty(prefix = "n11.inventory.low-stock", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LowStockScanner {

    private final InventoryProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final EntityManager em;

    /**
     * Default cron: every day at 09:00 UTC.  Override with
     * {@code n11.inventory.low-stock.cron} env in compose for testing
     * (e.g. {@code "0 *\/5 * * * *"} for a 5-minute demo cadence).
     */
    @Scheduled(cron = "${n11.inventory.low-stock.cron:0 0 9 * * *}")
    @Transactional(readOnly = true)
    public void scanAndPublish() {
        int threshold = properties.threshold();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, name, slug, stock
                  FROM products
                 WHERE stock <= :threshold
                 ORDER BY stock ASC, id ASC
                 LIMIT :limit
                """)
                .setParameter("threshold", threshold)
                .setParameter("limit", properties.maxItemsPerReport())
                .getResultList();

        if (rows.isEmpty()) {
            log.debug("Low-stock scan: no products at or below threshold {} — skipping mail", threshold);
            return;
        }

        List<LowStockReportEvent.Item> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new LowStockReportEvent.Item(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    (String) row[2],
                    ((Number) row[3]).intValue()));
        }

        LowStockReportEvent event = LowStockReportEvent.of(threshold, items);
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE, SagaTopology.RoutingKey.LOW_STOCK_REPORT, event);
        log.info("Low-stock scan: published report with {} items (threshold={})", items.size(), threshold);
    }
}
