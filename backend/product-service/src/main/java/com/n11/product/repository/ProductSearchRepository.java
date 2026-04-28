package com.n11.product.repository;

import com.n11.product.api.dto.SearchSort;
import com.n11.product.domain.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Native-SQL search using PostgreSQL FTS + filters.
 *
 * <p>Why a custom @Repository instead of a Spring Data @Query:
 * <ul>
 *   <li>tsvector / @@ / plainto_tsquery aren't valid JPQL — would need
 *       cast(... as ...) acrobatics that don't survive the query parser.</li>
 *   <li>ts_rank as a SELECT expression for ORDER BY relevance can't be
 *       expressed in JPA.</li>
 *   <li>Dynamic predicate building (price range optional, rating optional,
 *       multi-category IN list optional) is messier in JPQL than just
 *       building the SQL string from a few flags.</li>
 * </ul>
 * </p>
 *
 * <p>Pagination is done server-side via LIMIT/OFFSET and a parallel
 * COUNT(*) query — same pattern Spring Data uses internally.  Returns
 * {@link Product} entities so the existing ProductMapper.toSummary
 * still applies on the service layer.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    private final EntityManager em;

    public Page<Product> search(SearchCriteria c, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Args args = new Args();

        if (c.q() != null && !c.q().isBlank()) {
            where.append(" AND p.search_tsv @@ plainto_tsquery('turkish', unaccent(:q)) ");
            args.put("q", c.q().trim());
        }
        if (c.categoryIds() != null && !c.categoryIds().isEmpty()) {
            where.append(" AND p.category_id IN (:categoryIds) ");
            args.put("categoryIds", c.categoryIds());
        }
        if (c.minPrice() != null) {
            where.append(" AND p.price >= :minPrice ");
            args.put("minPrice", c.minPrice());
        }
        if (c.maxPrice() != null) {
            where.append(" AND p.price <= :maxPrice ");
            args.put("maxPrice", c.maxPrice());
        }
        if (c.minRating() != null) {
            where.append(" AND p.rating_average >= :minRating ");
            args.put("minRating", c.minRating());
        }
        if (c.inStockOnly()) {
            where.append(" AND p.stock > 0 ");
        }

        String orderBy = orderClause(c);

        // Page query
        String pageSql = "SELECT p.* FROM products p " + where + orderBy
                + " LIMIT :limit OFFSET :offset";
        Query pageQuery = em.createNativeQuery(pageSql, Product.class);
        args.applyTo(pageQuery);
        pageQuery.setParameter("limit", pageable.getPageSize());
        pageQuery.setParameter("offset", pageable.getOffset());
        @SuppressWarnings("unchecked")
        List<Product> results = pageQuery.getResultList();

        // Count query — same WHERE without ORDER/LIMIT
        String countSql = "SELECT count(*) FROM products p " + where;
        Query countQuery = em.createNativeQuery(countSql);
        args.applyTo(countQuery);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Per-category counts for the filter sidebar — applies the SAME filters
     * as {@link #search} except categoryIds, so the user can see how many
     * products would match in each *other* category if they switched.
     */
    public List<Object[]> categoryFacets(SearchCriteria c) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Args args = new Args();

        if (c.q() != null && !c.q().isBlank()) {
            where.append(" AND p.search_tsv @@ plainto_tsquery('turkish', unaccent(:q)) ");
            args.put("q", c.q().trim());
        }
        if (c.minPrice() != null) {
            where.append(" AND p.price >= :minPrice ");
            args.put("minPrice", c.minPrice());
        }
        if (c.maxPrice() != null) {
            where.append(" AND p.price <= :maxPrice ");
            args.put("maxPrice", c.maxPrice());
        }
        if (c.minRating() != null) {
            where.append(" AND p.rating_average >= :minRating ");
            args.put("minRating", c.minRating());
        }
        if (c.inStockOnly()) {
            where.append(" AND p.stock > 0 ");
        }

        String sql = "SELECT c.id, c.name, count(p.id) "
                + "FROM categories c LEFT JOIN products p ON p.category_id = c.id "
                + where.toString().replace("WHERE 1=1", "AND 1=1")
                + " GROUP BY c.id, c.name ORDER BY count(p.id) DESC, c.name ASC ";
        Query q = em.createNativeQuery(sql);
        args.applyTo(q);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        return rows;
    }

    private String orderClause(SearchCriteria c) {
        SearchSort sort = c.sort() == null ? SearchSort.RELEVANCE : c.sort();
        return switch (sort) {
            case RELEVANCE -> (c.q() != null && !c.q().isBlank())
                    // ts_rank uses the same query expression as the WHERE clause
                    ? " ORDER BY ts_rank(p.search_tsv, plainto_tsquery('turkish', unaccent(:q))) DESC, p.rating_count DESC "
                    // No query → relevance falls back to popularity
                    : " ORDER BY p.rating_count DESC, p.created_at DESC ";
            case PRICE_ASC  -> " ORDER BY p.price ASC, p.id ASC ";
            case PRICE_DESC -> " ORDER BY p.price DESC, p.id DESC ";
            case RATING     -> " ORDER BY p.rating_average DESC, p.rating_count DESC ";
            case NEWEST     -> " ORDER BY p.created_at DESC, p.id DESC ";
        };
    }

    public record SearchCriteria(
            String q,
            Set<Long> categoryIds,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minRating,
            boolean inStockOnly,
            SearchSort sort
    ) {}

    /** Tiny holder so we apply the same parameter map to count + page queries. */
    private static final class Args {
        private final java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        void put(String k, Object v) { map.put(k, v); }
        void applyTo(Query q) { map.forEach(q::setParameter); }
    }
}
