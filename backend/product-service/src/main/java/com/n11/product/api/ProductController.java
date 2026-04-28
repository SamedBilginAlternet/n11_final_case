package com.n11.product.api;

import com.n11.product.api.dto.AutocompleteSuggestion;
import com.n11.product.api.dto.PageResponse;
import com.n11.product.api.dto.ProductDetailDto;
import com.n11.product.api.dto.ProductSummaryDto;
import com.n11.product.api.dto.SearchFacetsDto;
import com.n11.product.api.dto.SearchSort;
import com.n11.product.service.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private final ProductQueryService service;

    @Operation(summary = "Paginated product search with FTS + filters + sort")
    @GetMapping
    public PageResponse<ProductSummaryDto> list(
            @Parameter(description = "Search term — Turkish FTS over name + description")
            @RequestParam(required = false) String q,

            @Parameter(description = "Filter by single category id (back-compat shorthand)")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Filter by category slug")
            @RequestParam(required = false) String category,

            @Parameter(description = "Filter by multiple category ids (sidebar checkboxes)")
            @RequestParam(name = "categoryIds", required = false) Set<Long> categoryIds,

            @Parameter(description = "Minimum price (inclusive)")
            @RequestParam(required = false) BigDecimal minPrice,

            @Parameter(description = "Maximum price (inclusive)")
            @RequestParam(required = false) BigDecimal maxPrice,

            @Parameter(description = "Minimum average rating (1..5)")
            @RequestParam(required = false) BigDecimal minRating,

            @Parameter(description = "Hide out-of-stock products")
            @RequestParam(name = "inStockOnly", defaultValue = "false") boolean inStockOnly,

            @Parameter(description = "Sort: relevance | price_asc | price_desc | rating | newest")
            @RequestParam(required = false) String sort,

            @PageableDefault(size = 12) Pageable pageable
    ) {
        return PageResponse.of(service.search(
                q, categoryId, category, categoryIds,
                minPrice, maxPrice, minRating, inStockOnly,
                SearchSort.from(sort), pageable));
    }

    @Operation(summary = "Sidebar facet counts for the current filter set (category counts, price range, total)")
    @GetMapping("/facets")
    public SearchFacetsDto facets(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(name = "categoryIds", required = false) Set<Long> categoryIds,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(name = "inStockOnly", defaultValue = "false") boolean inStockOnly
    ) {
        return service.facets(q, categoryId, category, categoryIds,
                minPrice, maxPrice, minRating, inStockOnly);
    }

    @Operation(summary = "Get product by id")
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ProductDetailDto> byId(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @Operation(summary = "Get product by slug")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductDetailDto> bySlug(@PathVariable String slug) {
        return ResponseEntity.ok(service.findBySlug(slug));
    }

    @Operation(summary = "Header search-bar autocomplete — top suggestions for a prefix")
    @GetMapping("/autocomplete")
    public List<AutocompleteSuggestion> autocomplete(
            @Parameter(description = "Search prefix") @RequestParam String q,
            @Parameter(description = "Max suggestions") @RequestParam(defaultValue = "8") int limit) {
        return service.autocomplete(q, limit);
    }
}
