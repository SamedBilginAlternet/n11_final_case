package com.n11.product.api;

import com.n11.product.api.dto.CategoryDto;
import com.n11.product.api.mapper.ProductMapper;
import com.n11.product.repository.CategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
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
}
