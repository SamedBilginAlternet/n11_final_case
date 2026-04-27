package com.n11.product.api;

import com.n11.product.api.dto.AutocompleteSuggestion;
import com.n11.product.api.dto.PageResponse;
import com.n11.product.api.dto.ProductDetailDto;
import com.n11.product.api.dto.ProductSummaryDto;
import com.n11.product.service.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private final ProductQueryService service;

    @Operation(summary = "Paginated product list with optional category and search filter")
    @GetMapping
    public PageResponse<ProductSummaryDto> list(
            @Parameter(description = "Filter by category id") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Filter by category slug") @RequestParam(required = false) String category,
            @Parameter(description = "Search term across name + description") @RequestParam(required = false) String q,
            @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.of(service.list(categoryId, category, q, pageable));
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
