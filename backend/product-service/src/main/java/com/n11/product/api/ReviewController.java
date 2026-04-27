package com.n11.product.api;

import com.n11.common.security.AuthenticatedUser;
import com.n11.product.api.dto.ReviewDto;
import com.n11.product.api.dto.ReviewRequest;
import com.n11.product.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Per-product reviews")
public class ReviewController {

    private final ReviewService service;

    @Operation(summary = "List reviews for a product, newest first")
    @GetMapping
    public Page<ReviewDto> list(@PathVariable Long productId,
                                @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(productId, pageable);
    }

    @Operation(summary = "Return the caller's review for this product, if any")
    @GetMapping("/mine")
    public ResponseEntity<ReviewDto> mine(@PathVariable Long productId,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        ReviewDto dto = service.myReview(productId, user.userId());
        return dto == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(dto);
    }

    @Operation(summary = "Create or update the caller's review (one per user per product)")
    @PutMapping
    public ReviewDto upsert(@PathVariable Long productId,
                            @AuthenticationPrincipal AuthenticatedUser user,
                            @RequestBody @Valid ReviewRequest body) {
        return service.upsert(productId, user.userId(), user.email(), body);
    }

    @Operation(summary = "Delete the caller's review")
    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable Long productId,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(productId, user.userId());
        return ResponseEntity.noContent().build();
    }
}
