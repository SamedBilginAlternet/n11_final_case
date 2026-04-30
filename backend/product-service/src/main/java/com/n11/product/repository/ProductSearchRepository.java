package com.n11.product.repository;

import com.n11.product.api.dto.SearchSort;
import com.n11.product.domain.Product;
import jakarta.persistence.EntityManager;
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
 *       multi-category IN list optional) is fed through
 *       {@link DynamicNativeQuery} so search and category-facet queries
 *       share one definition of "the filters".</li>
 * </ul>
 *
 * <p>Pagination is server-side via LIMIT/OFFSET and a parallel COUNT(*)
 * query — same pattern Spring Data uses internally. Returns
 * {@link Product} entities so the existing ProductMapper.toSummary still
 * applies on the service layer.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    private final EntityManager em;

    public Page<Product> search(SearchCriteria c, Pageable pageable) {
        String trimmedQ = trimToNull(c.q());

        DynamicNativeQuery pageQuery = applyFilters(
                DynamicNativeQuery.select("SELECT p.* FROM products p"), c, trimmedQ)
                .append(orderClause(c.sort(), trimmedQ))
                .append("LIMIT :limit OFFSET :offset")
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Product> results = pageQuery.toJpaQuery(em, Product.class).getResultList();

        long total = ((Number) applyFilters(
                DynamicNativeQuery.select("SELECT count(*) FROM products p"), c, trimmedQ)
                .toJpaQuery(em)
                .getSingleResult()).longValue();

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Per-category counts for the filter sidebar — applies the SAME filters
     * as {@link #search} except categoryIds, so the user can see how many
     * products would match in each <em>other</em> category if they switched.
     *
     * <p>Filters live inside the LEFT JOIN's ON clause (not WHERE) so
     * empty categories still appear with count(p.id)=0.</p>
     */
    public List<Object[]> categoryFacets(SearchCriteria c) {
        String trimmedQ = trimToNull(c.q());

        DynamicNativeQuery query = applyFacetFilters(
                DynamicNativeQuery.extend(
                        "SELECT c.id, c.name, count(p.id) "
                        + "FROM categories c LEFT JOIN products p ON p.category_id = c.id"),
                c, trimmedQ)
                .append("GROUP BY c.id, c.name")
                .append("ORDER BY count(p.id) DESC, c.name ASC");

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.toJpaQuery(em).getResultList();
        return rows;
    }

    /** Filters used by both search and facets — keep the lists in sync here, not at every call site. */
    private DynamicNativeQuery applyCommonFilters(DynamicNativeQuery q, SearchCriteria c, String trimmedQ) {
        return q
                .whenPresent(trimmedQ,      "p.search_tsv @@ plainto_tsquery('turkish', unaccent(:q))", "q")
                .whenPresent(c.minPrice(),  "p.price >= :minPrice",            "minPrice")
                .whenPresent(c.maxPrice(),  "p.price <= :maxPrice",            "maxPrice")
                .whenPresent(c.minRating(), "p.rating_average >= :minRating",  "minRating")
                .whenTrue(c.inStockOnly(),  "p.stock > 0");
    }

    /** Search adds the categoryIds filter on top of the common ones. */
    private DynamicNativeQuery applyFilters(DynamicNativeQuery q, SearchCriteria c, String trimmedQ) {
        return applyCommonFilters(q, c, trimmedQ)
                .whenPresent(c.categoryIds(), "p.category_id IN (:categoryIds)", "categoryIds");
    }

    /** Facet counts deliberately ignore categoryIds — the user wants to see the *other* categories. */
    private DynamicNativeQuery applyFacetFilters(DynamicNativeQuery q, SearchCriteria c, String trimmedQ) {
        return applyCommonFilters(q, c, trimmedQ);
    }

    private String orderClause(SearchSort sort, String trimmedQ) {
        SearchSort effective = sort == null ? SearchSort.RELEVANCE : sort;
        return switch (effective) {
            case RELEVANCE -> trimmedQ != null
                    // ts_rank uses the same query expression as the WHERE clause
                    ? "ORDER BY ts_rank(p.search_tsv, plainto_tsquery('turkish', unaccent(:q))) DESC, p.rating_count DESC"
                    // No query → relevance falls back to popularity
                    : "ORDER BY p.rating_count DESC, p.created_at DESC";
            case PRICE_ASC  -> "ORDER BY p.price ASC, p.id ASC";
            case PRICE_DESC -> "ORDER BY p.price DESC, p.id DESC";
            case RATING     -> "ORDER BY p.rating_average DESC, p.rating_count DESC";
            case NEWEST     -> "ORDER BY p.created_at DESC, p.id DESC";
        };
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
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
}
