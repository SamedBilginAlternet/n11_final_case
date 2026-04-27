package com.n11.product.service;

import com.n11.product.api.dto.ReviewDto;
import com.n11.product.api.dto.ReviewRequest;
import com.n11.product.domain.Product;
import com.n11.product.domain.Review;
import com.n11.product.repository.ProductRepository;
import com.n11.product.repository.ReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reviews + denormalized aggregate maintenance.
 *
 * <p>Each write recomputes the product's avg/count from the reviews table
 * and stores the result on the product row. Reads (product list / detail)
 * never touch the reviews table — they keep using the cached numeric columns
 * we already had, so list pages stay fast.</p>
 *
 * <p>The product cache (products:byId/Slug) is evicted on every write so a
 * fresh GET reflects the new aggregate immediately. The 5-minute TTL is the
 * fallback for any path we forgot to evict from.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<ReviewDto> list(Long productId, Pageable pageable) {
        ensureProductExists(productId);
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(ReviewService::toDto);
    }

    @Transactional(readOnly = true)
    public ReviewDto myReview(Long productId, Long userId) {
        return reviewRepository.findByProductIdAndUserId(productId, userId)
                .map(ReviewService::toDto)
                .orElse(null);
    }

    @Transactional
    @CacheEvict(value = {"products:byId", "products:bySlug"}, allEntries = true)
    public ReviewDto upsert(Long productId, Long userId, String userName, ReviewRequest req) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("product.not_found"));

        Review review = reviewRepository.findByProductIdAndUserId(productId, userId)
                .orElseGet(() -> Review.builder()
                        .productId(productId)
                        .userId(userId)
                        .userName(userName)
                        .build());

        review.setRating(req.rating());
        review.setBody(req.body());
        review.setUserName(userName); // keep snapshot fresh in case the user renamed
        Review saved = reviewRepository.save(review);

        recomputeAggregate(product);
        log.info("Review upserted productId={} userId={} rating={} avg={}",
                productId, userId, saved.getRating(), product.getRatingAverage());
        return toDto(saved);
    }

    @Transactional
    @CacheEvict(value = {"products:byId", "products:bySlug"}, allEntries = true)
    public void delete(Long productId, Long userId) {
        Review existing = reviewRepository.findByProductIdAndUserId(productId, userId)
                .orElseThrow(() -> new EntityNotFoundException("review.not_found"));
        reviewRepository.delete(existing);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("product.not_found"));
        recomputeAggregate(product);
    }

    private void recomputeAggregate(Product product) {
        long count = reviewRepository.countByProductId(product.getId());
        double avg = count == 0 ? 0.0 : reviewRepository.averageRating(product.getId());
        product.setRatingCount((int) count);
        product.setRatingAverage(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        productRepository.save(product);
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("product.not_found");
        }
    }

    public static ReviewDto toDto(Review r) {
        return new ReviewDto(r.getId(), r.getProductId(), r.getUserId(), r.getUserName(),
                r.getRating(), r.getBody(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
