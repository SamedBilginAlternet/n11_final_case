package com.n11.cart.service;

import com.n11.cart.api.dto.AddItemRequest;
import com.n11.cart.api.dto.CartDto;
import com.n11.cart.api.mapper.CartMapper;
import com.n11.cart.client.ProductClient;
import com.n11.cart.client.ProductSnapshot;
import com.n11.cart.domain.Cart;
import com.n11.cart.domain.CartItem;
import com.n11.cart.domain.Coupon;
import com.n11.cart.exception.InsufficientStockException;
import com.n11.cart.pricing.DiscountEngine;
import com.n11.cart.pricing.Quote;
import com.n11.cart.repository.CartRepository;
import com.n11.cart.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final CartMapper mapper;
    private final DiscountEngine discountEngine;
    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public CartDto get(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::quoteAndMap)
                .orElseGet(() -> mapper.empty(userId));
    }

    @Transactional
    public CartDto addItem(Long userId, AddItemRequest request) {
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> createCart(userId));
        ProductSnapshot product = productClient.fetch(request.productId());
        if (product == null) {
            throw new ResponseStatusException(NOT_FOUND, "Product not found: " + request.productId());
        }

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(product.id())).findFirst();

        int targetQuantity = existing.map(CartItem::getQuantity).orElse(0) + request.quantity();
        if (product.stock() != null && targetQuantity > product.stock()) {
            throw new InsufficientStockException(
                    "İstenen miktar ürün stoğunu aşıyor (ürün #" + product.id() + ").");
        }

        if (existing.isPresent()) {
            existing.get().setQuantity(targetQuantity);
        } else {
            CartItem item = CartItem.builder()
                    .productId(product.id())
                    .productName(product.name())
                    .imageUrl(product.imageUrl())
                    .quantity(request.quantity())
                    .unitPrice(product.price())
                    .currency(product.currency())
                    .build();
            cart.addItem(item);
        }

        Cart saved = cartRepository.save(cart);
        log.info("Added productId={} qty={} to cartId={}", product.id(), request.quantity(), saved.getId());
        return quoteAndMap(saved);
    }

    @Transactional
    public CartDto updateQuantity(Long userId, Long itemId, Integer quantity) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart not found"));
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart item not found: " + itemId));
        ProductSnapshot product = productClient.fetch(item.getProductId());
        if (product != null && product.stock() != null && quantity > product.stock()) {
            throw new InsufficientStockException(
                    "İstenen miktar ürün stoğunu aşıyor (ürün #" + product.id() + ").");
        }
        item.setQuantity(quantity);
        return quoteAndMap(cartRepository.save(cart));
    }

    @Transactional
    public CartDto removeItem(Long userId, Long itemId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart not found"));
        CartItem target = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart item not found: " + itemId));
        cart.removeItem(target);
        return quoteAndMap(cartRepository.save(cart));
    }

    @Transactional
    public CartDto applyCoupon(Long userId, String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon code is required");
        }
        String normalised = code.trim().toUpperCase();

        Coupon coupon = couponRepository.findByCodeIgnoreCase(normalised)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));

        if (!coupon.isValidAt(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Coupon expired or fully redeemed");
        }

        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> createCart(userId));
        cart.setCouponCode(normalised);
        Cart saved = cartRepository.save(cart);
        log.info("Coupon {} attached to cartId={}", normalised, saved.getId());
        return quoteAndMap(saved);
    }

    @Transactional
    public CartDto clearCoupon(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart not found"));
        cart.setCouponCode(null);
        return quoteAndMap(cartRepository.save(cart));
    }

    @Transactional
    public void clear(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cart.setCouponCode(null);
            cartRepository.save(cart);
            log.info("Cleared cartId={} for userId={}", cart.getId(), userId);
        });
    }

    private CartDto quoteAndMap(Cart cart) {
        Quote quote = discountEngine.quote(cart);
        return mapper.toDto(cart, quote);
    }

    private Cart createCart(Long userId) {
        return cartRepository.save(Cart.builder().userId(userId).build());
    }
}
