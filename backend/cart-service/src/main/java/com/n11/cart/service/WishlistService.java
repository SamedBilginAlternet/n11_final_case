package com.n11.cart.service;

import com.n11.cart.api.dto.WishlistItemDto;
import com.n11.cart.client.ProductClient;
import com.n11.cart.client.ProductSnapshot;
import com.n11.cart.domain.WishlistItem;
import com.n11.cart.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Per-user wishlist service.
 *
 * <p>Storage is just (userId, productId) — product details are fetched
 * live from product-service when listing. Stale cached prices in a
 * wishlist would lie to the user, so we accept the per-list product
 * lookup cost in exchange for accuracy.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistService {

    private final WishlistRepository repository;
    private final ProductClient productClient;

    @Transactional(readOnly = true)
    public List<WishlistItemDto> list(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::hydrate)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public WishlistItemDto add(Long userId, Long productId) {
        // Validates the product exists before we persist a dangling pointer.
        ProductSnapshot product;
        try {
            product = productClient.fetch(productId);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(NOT_FOUND, "Ürün bulunamadı");
            }
            throw ex;
        }

        if (repository.existsByUserIdAndProductId(userId, productId)) {
            return toDto(product, repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .filter(w -> w.getProductId().equals(productId))
                    .findFirst()
                    .orElseThrow());
        }

        try {
            WishlistItem saved = repository.save(WishlistItem.builder()
                    .userId(userId)
                    .productId(productId)
                    .build());
            log.info("Wishlist add userId={} productId={}", userId, productId);
            return toDto(product, saved);
        } catch (DataIntegrityViolationException ex) {
            // Race: two concurrent adds. Existing row wins; return it.
            return toDto(product, repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .filter(w -> w.getProductId().equals(productId))
                    .findFirst()
                    .orElseThrow());
        }
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        repository.deleteByUserIdAndProductId(userId, productId);
    }

    /**
     * Convenience for the frontend's heart-button: flips state and tells
     * the caller which side it landed on.
     */
    @Transactional
    public ToggleResult toggle(Long userId, Long productId) {
        if (repository.existsByUserIdAndProductId(userId, productId)) {
            repository.deleteByUserIdAndProductId(userId, productId);
            return new ToggleResult(false);
        }
        add(userId, productId);
        return new ToggleResult(true);
    }

    private WishlistItemDto hydrate(WishlistItem item) {
        try {
            ProductSnapshot p = productClient.fetch(item.getProductId());
            return toDto(p, item);
        } catch (ResponseStatusException ex) {
            // Product was deleted — silently drop from the listing rather
            // than fail the whole call. Could schedule a cleanup; not now.
            log.warn("Wishlist item {} references missing product {}", item.getId(), item.getProductId());
            return null;
        }
    }

    private static WishlistItemDto toDto(ProductSnapshot p, WishlistItem item) {
        return new WishlistItemDto(p.id(), p.slug(), p.name(), p.imageUrl(),
                p.price(), p.currency(), p.stock(), item.getCreatedAt());
    }

    public record ToggleResult(boolean added) {}
}
