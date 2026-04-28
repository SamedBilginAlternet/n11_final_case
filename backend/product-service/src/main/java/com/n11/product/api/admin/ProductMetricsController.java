package com.n11.product.api.admin;

import com.n11.product.api.admin.ProductMetricsDto.CategoryShare;
import com.n11.product.api.admin.ProductMetricsDto.LowStockItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregations powering the admin dashboard's product side.
 *
 * <p>Two native queries:
 * <ol>
 *   <li>top categories — JOIN + GROUP BY + ORDER BY count desc, capped to 8
 *       so the donut chart doesn't explode if the catalog has 50 categories.</li>
 *   <li>low stock — products where stock &lt; threshold, sorted by stock asc
 *       (most urgent first), capped to 12 for the dashboard list.</li>
 * </ol>
 * </p>
 */
@RestController
@RequestMapping("/api/products/admin/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProductMetricsController {

    private static final int LOW_STOCK_LIMIT = 12;
    private static final int TOP_CATEGORIES_LIMIT = 8;

    private final EntityManager em;

    @GetMapping
    @Transactional(readOnly = true)
    public ProductMetricsDto metrics(@RequestParam(defaultValue = "10") int lowStockThreshold) {
        int threshold = Math.min(Math.max(lowStockThreshold, 1), 1000);

        long total = ((Number) em.createNativeQuery("SELECT count(*) FROM products").getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> lowRows = em.createNativeQuery("""
                SELECT id, name, slug, stock
                  FROM products
                 WHERE stock <= :threshold
                 ORDER BY stock ASC, id ASC
                 LIMIT :limit
                """)
                .setParameter("threshold", threshold)
                .setParameter("limit", LOW_STOCK_LIMIT)
                .getResultList();

        long lowCount = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM products WHERE stock <= :threshold")
                .setParameter("threshold", threshold)
                .getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> catRows = em.createNativeQuery("""
                SELECT c.id, c.name, count(p.id) AS cnt
                  FROM categories c
                  LEFT JOIN products p ON p.category_id = c.id
                 GROUP BY c.id, c.name
                 ORDER BY cnt DESC, c.name ASC
                 LIMIT :limit
                """)
                .setParameter("limit", TOP_CATEGORIES_LIMIT)
                .getResultList();

        List<LowStockItem> lowStock = new ArrayList<>();
        for (Object[] row : lowRows) {
            lowStock.add(new LowStockItem(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    (String) row[2],
                    ((Number) row[3]).intValue()));
        }
        List<CategoryShare> topCategories = new ArrayList<>();
        for (Object[] row : catRows) {
            topCategories.add(new CategoryShare(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    ((Number) row[2]).longValue()));
        }
        return new ProductMetricsDto(total, lowCount, threshold, lowStock, topCategories);
    }
}
