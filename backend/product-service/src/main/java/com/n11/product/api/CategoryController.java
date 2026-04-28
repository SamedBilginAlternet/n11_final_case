package com.n11.product.api;

import com.n11.product.api.dto.CategoryDto;
import com.n11.product.api.dto.CategoryWriteRequest;
import com.n11.product.api.mapper.ProductMapper;
import com.n11.product.domain.Category;
import com.n11.product.repository.CategoryRepository;
import com.n11.product.repository.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductMapper mapper;

    @Operation(summary = "List all categories sorted by name (cached, 1h TTL).")
    @GetMapping
    @Cacheable(cacheNames = "categories", key = "'all'")
    public List<CategoryDto> list() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                .map(mapper::toCategoryDto)
                .toList();
    }

    // ------------------------------------------------------ Admin CRUD

    @Operation(summary = "Admin — create a category")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(cacheNames = "categories", allEntries = true)
    @Transactional
    public CategoryDto create(@RequestBody @Valid CategoryWriteRequest body) {
        categoryRepository.findBySlug(body.slug()).ifPresent(c -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug zaten kullanılıyor: " + body.slug());
        });
        Category saved = categoryRepository.save(Category.builder()
                .name(body.name())
                .slug(body.slug())
                .description(body.description())
                .build());
        return mapper.toCategoryDto(saved);
    }

    @Operation(summary = "Admin — update a category")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    @CacheEvict(cacheNames = "categories", allEntries = true)
    @Transactional
    public CategoryDto update(@PathVariable Long id, @RequestBody @Valid CategoryWriteRequest body) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kategori bulunamadı: " + id));
        if (!c.getSlug().equals(body.slug())) {
            Optional<Category> other = categoryRepository.findBySlug(body.slug());
            if (other.isPresent() && !other.get().getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug zaten kullanılıyor: " + body.slug());
            }
        }
        c.setName(body.name());
        c.setSlug(body.slug());
        c.setDescription(body.description());
        return mapper.toCategoryDto(c);
    }

    @Operation(summary = "Admin — delete a category (only if no products reference it)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CacheEvict(cacheNames = "categories", allEntries = true)
    @Transactional
    public void delete(@PathVariable Long id) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kategori bulunamadı: " + id));
        long inUse = productRepository.countByCategoryId(id);
        if (inUse > 0) {
            // The FK is ON DELETE RESTRICT so the database would reject this anyway,
            // but a 409 with a concrete count is more useful to the admin than a
            // generic SQLException stack trace.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu kategoride " + inUse + " ürün var, önce ürünleri başka kategoriye taşı.");
        }
        categoryRepository.delete(c);
    }
}
