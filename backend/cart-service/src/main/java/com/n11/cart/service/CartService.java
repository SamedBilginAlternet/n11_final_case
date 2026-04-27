package com.n11.cart.service;

import com.n11.cart.api.dto.AddItemRequest;
import com.n11.cart.api.dto.CartDto;
import com.n11.cart.api.dto.CartItemDto;
import com.n11.cart.client.ProductClient;
import com.n11.cart.client.ProductSnapshot;
import com.n11.cart.domain.Cart;
import com.n11.cart.domain.CartItem;
import com.n11.cart.exception.InsufficientStockException;
import com.n11.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    @Transactional(readOnly = true)
    public CartDto get(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::toDto)
                .orElseGet(() -> emptyDto(userId));
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
                    "Requested quantity exceeds available stock for product " + product.id());
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
        return toDto(saved);
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
                    "Requested quantity exceeds available stock for product " + product.id());
        }
        item.setQuantity(quantity);
        return toDto(cartRepository.save(cart));
    }

    @Transactional
    public CartDto removeItem(Long userId, Long itemId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart not found"));
        CartItem target = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart item not found: " + itemId));
        cart.removeItem(target);
        return toDto(cartRepository.save(cart));
    }

    @Transactional
    public void clear(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
            log.info("Cleared cartId={} for userId={}", cart.getId(), userId);
        });
    }

    private Cart createCart(Long userId) {
        return cartRepository.save(Cart.builder().userId(userId).build());
    }

    private CartDto emptyDto(Long userId) {
        return new CartDto(null, userId, List.of(), BigDecimal.ZERO, "TRY", 0);
    }

    private CartDto toDto(Cart cart) {
        List<CartItemDto> items = cart.getItems().stream()
                .map(i -> new CartItemDto(
                        i.getId(),
                        i.getProductId(),
                        i.getProductName(),
                        i.getImageUrl(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())),
                        i.getCurrency()))
                .toList();

        BigDecimal total = items.stream().map(CartItemDto::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int qty = items.stream().mapToInt(CartItemDto::quantity).sum();
        String currency = items.isEmpty() ? "TRY" : items.get(0).currency();
        return new CartDto(cart.getId(), cart.getUserId(), items, total, currency, qty);
    }
}
