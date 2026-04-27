package com.n11.cart.api;

import com.n11.cart.api.dto.WishlistItemDto;
import com.n11.cart.service.WishlistService;
import com.n11.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist", description = "Per-user product wishlist")
@SecurityRequirement(name = "bearerAuth")
public class WishlistController {

    private final WishlistService service;

    @Operation(summary = "List the caller's wishlist (newest first), each item hydrated from product-service")
    @GetMapping
    public List<WishlistItemDto> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.userId());
    }

    @Operation(summary = "Add a product to the wishlist (idempotent)")
    @PostMapping("/{productId}")
    public WishlistItemDto add(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable Long productId) {
        return service.add(user.userId(), productId);
    }

    @Operation(summary = "Toggle wishlist membership for a product — returns {added: true|false}")
    @PostMapping("/{productId}/toggle")
    public Map<String, Boolean> toggle(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long productId) {
        return Map.of("added", service.toggle(user.userId(), productId).added());
    }

    @Operation(summary = "Remove a product from the wishlist")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long productId) {
        service.remove(user.userId(), productId);
        return ResponseEntity.noContent().build();
    }
}
