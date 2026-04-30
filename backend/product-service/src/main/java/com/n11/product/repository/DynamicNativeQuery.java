package com.n11.product.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for native SQL queries with optional, value-driven filters.
 *
 * <p>Spring Data JPA's {@code Specification} / Criteria API don't speak
 * PostgreSQL FTS ({@code tsvector}, {@code @@}, {@code plainto_tsquery},
 * {@code ts_rank}). When you have to drop to native SQL, the naive style is
 * a chain of {@code if (criteria.foo() != null) { sql.append(" AND ..."); }}
 * blocks plus a parallel parameter map — repeated for every query that
 * needs the same filters. This helper keeps the WHERE-clause assembly in
 * one place: each call site declares <em>what</em> filter to apply, not
 * <em>how</em> to splice it in or how to track the WHERE/AND state.</p>
 *
 * <p>Two start modes:
 * <ul>
 *   <li>{@link #select(String)} — base SQL has no WHERE yet; first applied
 *       filter prepends {@code WHERE}, the rest {@code AND}.</li>
 *   <li>{@link #extend(String)} — base SQL already ended with a predicate
 *       (e.g. a JOIN's {@code ON} clause), so every applied filter prepends
 *       {@code AND}. Lets us keep facet filters inside the LEFT JOIN's ON
 *       so empty categories stay visible — without the
 *       {@code "WHERE 1=1" → "AND 1=1"} string-replace trick.</li>
 * </ul>
 *
 * <p>Currently only product-service writes raw native SQL — promote this
 * to {@code common.persistence} the day a second service needs the same
 * pattern.</p>
 */
public final class DynamicNativeQuery {

    private final StringBuilder sql;
    private final Map<String, Object> params = new LinkedHashMap<>();
    private final String firstPrefix;
    private boolean anyClauseAdded;

    private DynamicNativeQuery(String baseSql, String firstPrefix) {
        this.sql = new StringBuilder(baseSql);
        this.firstPrefix = firstPrefix;
    }

    /** Base SQL with no WHERE yet — first filter prepends WHERE. */
    public static DynamicNativeQuery select(String baseSql) {
        return new DynamicNativeQuery(baseSql, " WHERE ");
    }

    /** Base SQL already ended in a predicate (e.g. a JOIN ON) — every filter chains with AND. */
    public static DynamicNativeQuery extend(String baseSql) {
        return new DynamicNativeQuery(baseSql, " AND ");
    }

    /**
     * Add the clause and bind the parameter only when {@code value} is
     * "present" — non-null, non-blank string, non-empty collection/map.
     *
     * @param value     value to test and bind; the clause must reference it
     *                  by {@code :paramName}
     * @param clause    SQL fragment without leading {@code AND}; e.g.
     *                  {@code "p.price >= :minPrice"}
     * @param paramName name of the parameter (without the colon)
     */
    public <T> DynamicNativeQuery whenPresent(T value, String clause, String paramName) {
        if (isPresent(value)) {
            appendClause(clause);
            params.put(paramName, value);
        }
        return this;
    }

    /** Add the clause when the boolean is true; no parameter binding. */
    public DynamicNativeQuery whenTrue(boolean condition, String clause) {
        if (condition) appendClause(clause);
        return this;
    }

    /** Append a literal SQL fragment (ORDER BY, LIMIT, GROUP BY, …). */
    public DynamicNativeQuery append(String fragment) {
        sql.append(' ').append(fragment).append(' ');
        return this;
    }

    /** Bind a parameter referenced by an {@link #append}-ed fragment (e.g. LIMIT). */
    public DynamicNativeQuery bind(String paramName, Object value) {
        params.put(paramName, value);
        return this;
    }

    public String sql() { return sql.toString(); }

    public Query toJpaQuery(EntityManager em) {
        Query q = em.createNativeQuery(sql.toString());
        params.forEach(q::setParameter);
        return q;
    }

    public Query toJpaQuery(EntityManager em, Class<?> resultClass) {
        Query q = em.createNativeQuery(sql.toString(), resultClass);
        params.forEach(q::setParameter);
        return q;
    }

    private void appendClause(String clause) {
        sql.append(anyClauseAdded ? " AND " : firstPrefix).append(clause);
        anyClauseAdded = true;
    }

    private static boolean isPresent(Object v) {
        if (v == null) return false;
        if (v instanceof CharSequence cs) return !cs.toString().isBlank();
        if (v instanceof Collection<?> c) return !c.isEmpty();
        if (v instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }
}
