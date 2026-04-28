package com.n11.product.service;

import com.n11.product.api.dto.ProductDetailDto;
import com.n11.product.api.dto.ProductWriteRequest;
import com.n11.product.api.mapper.ProductMapper;
import com.n11.product.domain.Category;
import com.n11.product.domain.Product;
import com.n11.product.repository.CategoryRepository;
import com.n11.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Admin-only product CRUD.
 *
 * <p>SecurityConfig should already gate POST/PUT/DELETE under /api/products
 * to ADMIN role; this service trusts that and only worries about business
 * rules: slug uniqueness, category existence, currency normalisation.</p>
 *
 * <p>Cache eviction is wide on every write — pages, byId, bySlug,
 * autocomplete and recommendations can all reference a product, so a
 * single edit invalidates them all.  The recommendations cache is keyed
 * by seed product id, so we evict its entire namespace on any product
 * write rather than try to be clever about which seeds reference the
 * edited item.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProductAdminService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;

    @Caching(evict = {
            @CacheEvict(cacheNames = "products:bySlug", allEntries = true),
            @CacheEvict(cacheNames = "products:byId", allEntries = true),
            @CacheEvict(cacheNames = "products:autocomplete", allEntries = true),
            @CacheEvict(cacheNames = "recommendations", allEntries = true),
    })
    public ProductDetailDto create(ProductWriteRequest req) {
        productRepository.findBySlug(req.slug()).ifPresent(p -> {
            throw new ResponseStatusException(CONFLICT, "Slug zaten kullanılıyor: " + req.slug());
        });
        Category category = lookupCategory(req.categoryId());
        Product p = Product.builder()
                .name(req.name())
                .slug(req.slug())
                .description(req.description())
                .price(req.price())
                .currency(normaliseCurrency(req.currency()))
                .stock(req.stock())
                .imageUrl(req.imageUrl())
                .category(category)
                .ratingAverage(BigDecimal.ZERO)
                .ratingCount(0)
                .build();
        Product saved = productRepository.save(p);
        log.info("Admin created product id={} slug={}", saved.getId(), saved.getSlug());
        return mapper.toDetail(saved);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "products:bySlug", allEntries = true),
            @CacheEvict(cacheNames = "products:byId", allEntries = true),
            @CacheEvict(cacheNames = "products:autocomplete", allEntries = true),
            @CacheEvict(cacheNames = "recommendations", allEntries = true),
    })
    public ProductDetailDto update(Long id, ProductWriteRequest req) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ürün bulunamadı: " + id));
        // Slug change: ensure no other product owns the new slug.
        if (!p.getSlug().equals(req.slug())) {
            Optional<Product> other = productRepository.findBySlug(req.slug());
            if (other.isPresent() && !other.get().getId().equals(id)) {
                throw new ResponseStatusException(CONFLICT, "Slug zaten kullanılıyor: " + req.slug());
            }
        }
        if (!p.getCategory().getId().equals(req.categoryId())) {
            p.setCategory(lookupCategory(req.categoryId()));
        }
        p.setName(req.name());
        p.setSlug(req.slug());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setCurrency(normaliseCurrency(req.currency()));
        p.setStock(req.stock());
        p.setImageUrl(req.imageUrl());
        Product saved = productRepository.save(p);
        log.info("Admin updated product id={} slug={}", saved.getId(), saved.getSlug());
        return mapper.toDetail(saved);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "products:bySlug", allEntries = true),
            @CacheEvict(cacheNames = "products:byId", allEntries = true),
            @CacheEvict(cacheNames = "products:autocomplete", allEntries = true),
            @CacheEvict(cacheNames = "recommendations", allEntries = true),
    })
    public void delete(Long id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ürün bulunamadı: " + id));
        productRepository.delete(p);
        log.info("Admin deleted product id={}", id);
    }

    private Category lookupCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Kategori bulunamadı: " + id));
    }

    private String normaliseCurrency(String c) {
        if (c == null || c.isBlank()) return "TRY";
        return c.trim().toUpperCase();
    }
}
