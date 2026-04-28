package com.n11.order.api.admin;

import com.n11.order.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregations for the admin dashboard.
 *
 * <p>Native SQL on purpose: date_trunc + GROUP BY isn't pleasant in JPQL,
 * and these queries scan the whole orders table once per dashboard load.
 * Everything is read-only and bounded by a {@code days} window so the
 * scan stays linear in trailing volume even as the table grows.</p>
 *
 * <p>Revenue counts CONFIRMED and later — PENDING/AWAITING_PAYMENT haven't
 * actually paid yet, CANCELLED was refunded.  This matches what the
 * finance team would expect on a "ciro" report.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderMetricsService {

    private static final List<String> REVENUE_STATUSES =
            List.of("CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED");

    private final EntityManager em;

    public OrderMetricsDto compute(int days) {
        int window = Math.min(Math.max(days, 1), 365);
        Instant startOfWindow = LocalDate.now(ZoneOffset.UTC)
                .minusDays(window - 1L)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        return new OrderMetricsDto(
                summary(startOfToday, startOfWindow),
                daily(startOfWindow, window),
                statusBreakdown());
    }

    private OrderMetricsDto.Summary summary(Instant startOfToday, Instant startOfWindow) {
        long todayOrders = scalarLong(
                "SELECT count(*) FROM orders WHERE created_at >= :since",
                Map.of("since", Timestamp.from(startOfToday)));
        BigDecimal todayRevenue = scalarBigDecimal(
                "SELECT COALESCE(SUM(total_amount), 0) FROM orders "
                        + "WHERE created_at >= :since AND status IN (:statuses)",
                Map.of("since", Timestamp.from(startOfToday), "statuses", REVENUE_STATUSES));
        long pendingOrders = scalarLong(
                "SELECT count(*) FROM orders WHERE status = 'CONFIRMED'",
                Map.of());
        BigDecimal totalRevenue = scalarBigDecimal(
                "SELECT COALESCE(SUM(total_amount), 0) FROM orders "
                        + "WHERE created_at >= :since AND status IN (:statuses)",
                Map.of("since", Timestamp.from(startOfWindow), "statuses", REVENUE_STATUSES));

        return new OrderMetricsDto.Summary(todayOrders, todayRevenue, pendingOrders, totalRevenue);
    }

    @SuppressWarnings("unchecked")
    private List<OrderMetricsDto.DailyPoint> daily(Instant startOfWindow, int window) {
        Query q = em.createNativeQuery("""
                SELECT date_trunc('day', created_at)::date AS d,
                       count(*) AS cnt,
                       COALESCE(SUM(CASE WHEN status IN ('CONFIRMED','PROCESSING','SHIPPED','DELIVERED')
                                         THEN total_amount ELSE 0 END), 0) AS rev
                  FROM orders
                 WHERE created_at >= :since
                 GROUP BY d
                 ORDER BY d
                """);
        q.setParameter("since", Timestamp.from(startOfWindow));
        List<Object[]> rows = q.getResultList();

        // Backfill missing days with zeros so the line chart doesn't have gaps.
        Map<LocalDate, OrderMetricsDto.DailyPoint> byDate = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate d = ((Date) row[0]).toLocalDate();
            byDate.put(d, new OrderMetricsDto.DailyPoint(
                    d,
                    ((Number) row[1]).longValue(),
                    new BigDecimal(row[2].toString())));
        }
        List<OrderMetricsDto.DailyPoint> out = new ArrayList<>(window);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = window - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            out.add(byDate.getOrDefault(d, new OrderMetricsDto.DailyPoint(d, 0L, BigDecimal.ZERO)));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<OrderMetricsDto.StatusSlice> statusBreakdown() {
        Query q = em.createNativeQuery("""
                SELECT status, count(*) FROM orders GROUP BY status ORDER BY count(*) DESC
                """);
        List<Object[]> rows = q.getResultList();
        List<OrderMetricsDto.StatusSlice> out = new ArrayList<>();
        for (Object[] row : rows) {
            out.add(new OrderMetricsDto.StatusSlice(
                    OrderStatus.valueOf((String) row[0]),
                    ((Number) row[1]).longValue()));
        }
        return out;
    }

    private long scalarLong(String sql, Map<String, Object> params) {
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        Object res = q.getSingleResult();
        return res == null ? 0L : ((Number) res).longValue();
    }

    private BigDecimal scalarBigDecimal(String sql, Map<String, Object> params) {
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        Object res = q.getSingleResult();
        return res == null ? BigDecimal.ZERO : new BigDecimal(res.toString());
    }

}
