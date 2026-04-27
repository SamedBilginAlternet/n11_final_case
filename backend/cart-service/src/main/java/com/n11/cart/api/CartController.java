package com.n11.cart.api;

import com.n11.cart.api.dto.AddItemRequest;
import com.n11.cart.api.dto.CartDto;
import com.n11.cart.api.dto.UpdateQuantityRequest;
import com.n11.cart.service.CartService;
import com.n11.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get the authenticated user's cart")
    @GetMapping
    public CartDto get(@AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.get(user.userId());
    }

    @Operation(summary = "Add an item to the cart (or increment quantity)")
    @PostMapping("/items")
    public ResponseEntity<CartDto> addItem(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestBody @Valid AddItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItem(user.userId(), request));
    }

    @Operation(summary = "Set quantity for a cart item")
    @PutMapping("/items/{itemId}")
    public CartDto updateQuantity(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable Long itemId,
                                  @RequestBody @Valid UpdateQuantityRequest request) {
        return cartService.updateQuantity(user.userId(), itemId, request.quantity());
    }

    @Operation(summary = "Remove a cart item")
    @DeleteMapping("/items/{itemId}")
    public CartDto removeItem(@AuthenticationPrincipal AuthenticatedUser user,
                              @PathVariable Long itemId) {
        return cartService.removeItem(user.userId(), itemId);
    }
}
