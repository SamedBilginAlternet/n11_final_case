package com.n11.product.service;

import com.n11.product.api.dto.ReviewDto;
import com.n11.product.api.dto.ReviewRequest;
import com.n11.product.domain.Product;
import com.n11.product.domain.Review;
import com.n11.product.repository.ProductRepository;
import com.n11.product.repository.ReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ProductRepository productRepository;
    @InjectMocks ReviewService service;

    @Test
    void firstReviewCreatesAndUpdatesAggregate() {
        Product product = Product.builder().id(1L).ratingAverage(BigDecimal.ZERO).ratingCount(0).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.findByProductIdAndUserId(1L, 7L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });
        when(reviewRepository.countByProductId(1L)).thenReturn(1L);
        when(reviewRepository.averageRating(1L)).thenReturn(5.0);

        ReviewDto dto = service.upsert(1L, 7L, "Ada", new ReviewRequest(5, "Harika"));

        assertThat(dto.rating()).isEqualTo(5);
        ArgumentCaptor<Product> savedProduct = ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository).save(savedProduct.capture());
        assertThat(savedProduct.getValue().getRatingCount()).isEqualTo(1);
        assertThat(savedProduct.getValue().getRatingAverage()).isEqualByComparingTo("5.00");
    }

    @Test
    void secondReviewFromSameUserUpdatesExistingRowNotCreatesNew() {
        Product product = Product.builder().id(1L).ratingAverage(new BigDecimal("4.00")).ratingCount(1).build();
        Review existing = Review.builder().id(50L).productId(1L).userId(7L).rating(4).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.findByProductIdAndUserId(1L, 7L)).thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.countByProductId(1L)).thenReturn(1L);
        when(reviewRepository.averageRating(1L)).thenReturn(2.0);

        ReviewDto dto = service.upsert(1L, 7L, "Ada", new ReviewRequest(2, "Pişmanım"));

        assertThat(dto.id()).isEqualTo(50L);
        assertThat(dto.rating()).isEqualTo(2);
    }

    @Test
    void rejectsReviewForUnknownProduct() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.upsert(404L, 7L, "Ada", new ReviewRequest(5, "Yok")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deleteRemovesAndZeroesAggregateWhenNoReviewsLeft() {
        Product product = Product.builder().id(1L).ratingAverage(new BigDecimal("3.00")).ratingCount(1).build();
        Review existing = Review.builder().id(50L).productId(1L).userId(7L).rating(3).build();
        when(reviewRepository.findByProductIdAndUserId(1L, 7L)).thenReturn(Optional.of(existing));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.countByProductId(1L)).thenReturn(0L);

        service.delete(1L, 7L);

        ArgumentCaptor<Product> savedProduct = ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository).save(savedProduct.capture());
        assertThat(savedProduct.getValue().getRatingCount()).isZero();
        assertThat(savedProduct.getValue().getRatingAverage()).isEqualByComparingTo("0.00");
    }
}
