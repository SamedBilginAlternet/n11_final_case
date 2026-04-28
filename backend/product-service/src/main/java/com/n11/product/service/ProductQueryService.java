package com.n11.product.service;

import com.n11.product.api.dto.AutocompleteSuggestion;
import com.n11.product.api.dto.ProductDetailDto;
import com.n11.product.api.dto.ProductSummaryDto;
import com.n11.product.api.dto.SearchFacetsDto;
import com.n11.product.api.dto.SearchSort;
import com.n11.product.api.mapper.ProductMapper;
import com.n11.product.repository.CategoryRepository;
import com.n11.product.repository.ProductRepository;
import com.n11.product.repository.ProductSearchRepository;
import com.n11.product.repository.ProductSearchRepository.SearchCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;
    private final EntityManager em;

    public Page<ProductSummaryDto> search(String q,
                                          Long categoryId,
                                          String categorySlug,
                                          Set<Long> categoryIds,
                                          BigDecimal minPrice,
                                          BigDecimal maxPrice,
                                          BigDecimal minRating,
                                          boolean inStockOnly,
                                          SearchSort sort,
                                          Pageable pageable) {
        Set<Long> resolvedCategoryIds = resolveCategoryFilter(categoryId, categorySlug, categoryIds);
        SearchCriteria criteria = new SearchCriteria(
                q, resolvedCategoryIds, minPrice, maxPrice, minRating, inStockOnly, sort);
        return searchRepository.search(criteria, pageable).map(mapper::toSummary);
    }

    public SearchFacetsDto facets(String q,
                                  Long categoryId,
                                  String categorySlug,
                                  Set<Long> categoryIds,
                                  BigDecimal minPrice,
                                  BigDecimal maxPrice,
                                  BigDecimal minRating,
                                  boolean inStockOnly) {
        // Category facets: counts ignore the active categoryIds filter so
        // the user sees how many products would match if they switched.
        SearchCriteria withoutCategory = new SearchCriteria(
                q, null, minPrice, maxPrice, minRating, inStockOnly, null);
        List<SearchFacetsDto.CategoryFacet> categories = new ArrayList<>();
        for (Object[] row : searchRepository.categoryFacets(withoutCategory)) {
            categories.add(new SearchFacetsDto.CategoryFacet(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    ((Number) row[2]).longValue()));
        }

        // Price min/max + total within full active filter (incl. category).
        Set<Long> resolvedCategoryIds = resolveCategoryFilter(categoryId, categorySlug, categoryIds);
        BigDecimal[] priceRange = priceRange(q, resolvedCategoryIds, minPrice, maxPrice, minRating, inStockOnly);
        long total = totalCount(q, resolvedCategoryIds, minPrice, maxPrice, minRating, inStockOnly);

        return new SearchFacetsDto(categories, priceRange[0], priceRange[1], total);
    }

    private Set<Long> resolveCategoryFilter(Long categoryId, String categorySlug, Set<Long> categoryIds) {
        if (categoryIds != null && !categoryIds.isEmpty()) return categoryIds;
        if (categoryId != null) return Set.of(categoryId);
        if (categorySlug != null && !categorySlug.isBlank()) {
            return Set.of(categoryRepository.findBySlug(categorySlug)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Category not found: " + categorySlug))
                    .getId());
        }
        return Set.of();
    }

    private BigDecimal[] priceRange(String q,
                                    Set<Long> categoryIds,
                                    BigDecimal minPrice,
                                    BigDecimal maxPrice,
                                    BigDecimal minRating,
                                    boolean inStockOnly) {
        // Use the search criteria but pull min/max via a single aggregate query
        // so the slider can clamp to whatever the live result set holds.
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(MIN(p.price), 0), COALESCE(MAX(p.price), 0) FROM products p WHERE 1=1 ");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND p.search_tsv @@ plainto_tsquery('turkish', unaccent(:q)) ");
            params.put("q", q.trim());
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            sql.append(" AND p.category_id IN (:categoryIds) ");
            params.put("categoryIds", categoryIds);
        }
        if (minPrice != null) { sql.append(" AND p.price >= :minPrice "); params.put("minPrice", minPrice); }
        if (maxPrice != null) { sql.append(" AND p.price <= :maxPrice "); params.put("maxPrice", maxPrice); }
        if (minRating != null) { sql.append(" AND p.rating_average >= :minRating "); params.put("minRating", minRating); }
        if (inStockOnly) sql.append(" AND p.stock > 0 ");

        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        Object[] row = (Object[]) query.getSingleResult();
        return new BigDecimal[] {
                row[0] == null ? BigDecimal.ZERO : new BigDecimal(row[0].toString()),
                row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString())
        };
    }

    private long totalCount(String q,
                            Set<Long> categoryIds,
                            BigDecimal minPrice,
                            BigDecimal maxPrice,
                            BigDecimal minRating,
                            boolean inStockOnly) {
        SearchCriteria criteria = new SearchCriteria(
                q, categoryIds, minPrice, maxPrice, minRating, inStockOnly, null);
        return searchRepository.search(criteria, PageRequest.of(0, 1)).getTotalElements();
    }

    @Cacheable(cacheNames = "products:byId", key = "#id")
    public ProductDetailDto findById(Long id) {
        return productRepository.findById(id)
                .map(mapper::toDetail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found: " + id));
    }

    @Cacheable(cacheNames = "products:bySlug", key = "#slug")
    public ProductDetailDto findBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .map(mapper::toDetail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found: " + slug));
    }

    @Cacheable(cacheNames = "products:autocomplete",
               key = "#q.trim().toLowerCase() + ':' + T(java.lang.Math).min(T(java.lang.Math).max(#limit, 1), 20)")
    public List<AutocompleteSuggestion> autocomplete(String q, int limit) {
        if (q == null || q.isBlank()) return List.of();
        int capped = Math.min(Math.max(limit, 1), 20);
        return productRepository.autocomplete(q.trim(), PageRequest.of(0, capped)).stream()
                .map(p -> new AutocompleteSuggestion(
                        p.getId(), p.getName(), p.getSlug(), p.getImageUrl(),
                        p.getCategory() != null ? p.getCategory().getName() : null))
                .toList();
    }
}
