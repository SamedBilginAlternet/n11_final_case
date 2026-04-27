package com.n11.product.service;

import com.n11.product.api.dto.ProductDetailDto;
import com.n11.product.api.dto.ProductSummaryDto;
import com.n11.product.api.mapper.ProductMapper;
import com.n11.product.repository.CategoryRepository;
import com.n11.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;

    public Page<ProductSummaryDto> list(Long categoryId, String categorySlug, String q, Pageable pageable) {
        Long resolvedCategoryId = categoryId;
        if (resolvedCategoryId == null && categorySlug != null && !categorySlug.isBlank()) {
            resolvedCategoryId = categoryRepository.findBySlug(categorySlug)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Category not found: " + categorySlug))
                    .getId();
        }
        String query = (q == null || q.isBlank()) ? null : q.trim();
        return productRepository.search(resolvedCategoryId, query, pageable).map(mapper::toSummary);
    }

    public ProductDetailDto findById(Long id) {
        return productRepository.findById(id)
                .map(mapper::toDetail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found: " + id));
    }

    public ProductDetailDto findBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .map(mapper::toDetail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found: " + slug));
    }
}
