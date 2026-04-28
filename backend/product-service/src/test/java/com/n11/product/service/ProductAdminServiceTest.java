package com.n11.product.service;

import com.n11.product.api.dto.CategoryDto;
import com.n11.product.api.dto.ProductDetailDto;
import com.n11.product.api.dto.ProductWriteRequest;
import com.n11.product.api.mapper.ProductMapper;
import com.n11.product.domain.Category;
import com.n11.product.domain.Product;
import com.n11.product.repository.CategoryRepository;
import com.n11.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductAdminServiceTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductMapper mapper;

    @InjectMocks ProductAdminService service;

    private ProductWriteRequest req() {
        return new ProductWriteRequest(
                "iPhone 15", "iphone-15", "açıklama",
                new BigDecimal("49999.00"), "TRY", 25, "https://...", 3L);
    }

    private Category category() {
        Category c = new Category();
        c.setId(3L);
        c.setName("Telefon");
        c.setSlug("telefon");
        return c;
    }

    @Test
    void createPersistsAndDefaultsRatingsToZero() {
        when(productRepository.findBySlug("iphone-15")).thenReturn(Optional.empty());
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category()));
        Product saved = Product.builder().id(1L).build();
        when(productRepository.save(any(Product.class))).thenReturn(saved);
        when(mapper.toDetail(saved)).thenReturn(detailStub(1L));

        ProductDetailDto out = service.create(req());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product persisted = captor.getValue();
        assertThat(persisted.getSlug()).isEqualTo("iphone-15");
        assertThat(persisted.getRatingAverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(persisted.getRatingCount()).isZero();
        assertThat(persisted.getCurrency()).isEqualTo("TRY");
        assertThat(out.id()).isEqualTo(1L);
    }

    @Test
    void createWithDuplicateSlugThrows409() {
        Product existing = Product.builder().id(99L).slug("iphone-15").build();
        when(productRepository.findBySlug("iphone-15")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Slug zaten kullanılıyor");
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateAcceptsSameSlugWithoutFalsePositive() {
        Product current = Product.builder()
                .id(1L).slug("iphone-15").currency("TRY")
                .ratingAverage(BigDecimal.ZERO).ratingCount(0)
                .category(category())
                .build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(current));
        when(productRepository.save(any(Product.class))).thenReturn(current);
        when(mapper.toDetail(current)).thenReturn(detailStub(1L));

        ProductDetailDto out = service.update(1L, req());
        assertThat(out.id()).isEqualTo(1L);
        // No findBySlug call expected because the slug didn't change.
        verify(productRepository, never()).findBySlug(any());
    }

    @Test
    void updateWithSlugConflictAcrossDifferentIdThrows() {
        Product current = Product.builder().id(1L).slug("old-slug").category(category()).build();
        Product otherWithNewSlug = Product.builder().id(7L).slug("iphone-15").build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(current));
        when(productRepository.findBySlug("iphone-15")).thenReturn(Optional.of(otherWithNewSlug));

        assertThatThrownBy(() -> service.update(1L, req()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Slug zaten kullanılıyor");
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteRemovesProduct() {
        Product p = Product.builder().id(1L).slug("x").build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        service.delete(1L);
        verify(productRepository).delete(p);
    }

    @Test
    void deleteMissingProductThrows404() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ürün bulunamadı");
    }

    private ProductDetailDto detailStub(Long id) {
        return new ProductDetailDto(
                id, "iPhone 15", "iphone-15", "desc",
                new BigDecimal("49999.00"), "TRY", 25, "img",
                BigDecimal.ZERO, 0,
                3L, "Telefon", "telefon",
                Instant.now(), Instant.now());
    }

    @SuppressWarnings("unused")
    private CategoryDto categoryDtoStub() {
        return new CategoryDto(3L, "Telefon", "telefon", null);
    }
}
