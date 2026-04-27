package com.n11.order.api;

import com.n11.common.security.AuthenticatedUser;
import com.n11.order.api.dto.OrderDto;
import com.n11.order.api.mapper.OrderMapper;
import com.n11.order.repository.OrderRepository;
import com.n11.order.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderRepository repository;
    private final OrderMapper mapper;

    @Operation(summary = "Checkout the current cart and emit OrderCreated saga event")
    @PostMapping("/checkout")
    public ResponseEntity<OrderDto> checkout(@AuthenticationPrincipal AuthenticatedUser user) {
        OrderDto dto = checkoutService.checkout(user.userId(), user.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    @Operation(summary = "List the authenticated user's orders, newest first")
    @GetMapping
    public List<OrderDto> list(@AuthenticationPrincipal AuthenticatedUser user,
                               @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(user.userId(), pageable)
                .map(mapper::toDto).getContent();
    }

    @Operation(summary = "Get a single order owned by the authenticated user")
    @GetMapping("/{id}")
    public OrderDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return repository.findByIdAndUserId(id, user.userId())
                .map(mapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
