package com.n11.order.api.admin;

import com.n11.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OrderMetricsDto(
        Summary summary,
        List<DailyPoint> daily,
        List<StatusSlice> statusBreakdown
) {
    public record Summary(
            long todayOrders,
            BigDecimal todayRevenue,
            long pendingOrders,
            BigDecimal totalRevenue
    ) {}

    public record DailyPoint(LocalDate date, long orderCount, BigDecimal revenue) {}

    public record StatusSlice(OrderStatus status, long count) {}
}
