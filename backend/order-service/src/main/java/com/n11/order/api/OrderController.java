package com.n11.order.api;

import com.n11.common.security.AuthenticatedUser;
import com.n11.order.api.admin.OrderMetricsDto;
import com.n11.order.api.admin.OrderMetricsService;
import com.n11.order.api.dto.OrderDto;
import com.n11.order.api.mapper.OrderMapper;
import com.n11.order.domain.OrderStatus;
import com.n11.order.repository.OrderRepository;
import com.n11.order.service.CheckoutService;
import com.n11.order.service.OrderStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final OrderStatusService statusService;
    private final OrderMetricsService metricsService;
    private final OrderRepository repository;
    private final OrderMapper mapper;

    @Operation(summary = "Checkout the current cart and emit OrderCreated saga event")
    @PostMapping("/checkout")
    public ResponseEntity<OrderDto> checkout(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestBody @Valid CheckoutRequest body) {
        OrderDto dto = checkoutService.checkout(user.userId(), user.email(), body.addressId());
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

    @Operation(summary = "Admin — list every user's orders, newest first, optionally filtered by status")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public List<OrderDto> adminList(@RequestParam(required = false) OrderStatus status,
                                    @PageableDefault(size = 20) Pageable pageable) {
        return repository.findAllByOptionalStatus(status, pageable).map(mapper::toDto).getContent();
    }

    @Operation(summary = "Admin — dashboard metrics (last N days, defaults 30)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/metrics")
    public OrderMetricsDto adminMetrics(@RequestParam(defaultValue = "30") int days) {
        return metricsService.compute(days);
    }

    @Operation(summary = "Admin — get any order by id (no user-scoped guard)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{id}")
    public OrderDto adminGet(@PathVariable Long id) {
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @Operation(summary = "Admin — move CONFIRMED order to PROCESSING")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/processing")
    public OrderDto markProcessing(@PathVariable Long id) {
        return statusService.markProcessing(id);
    }

    @Operation(summary = "Admin — move PROCESSING order to SHIPPED with carrier + tracking")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/shipped")
    public OrderDto markShipped(@PathVariable Long id,
                                @RequestBody(required = false) @Valid StatusUpdateRequest body) {
        return statusService.markShipped(id, body);
    }

    @Operation(summary = "Admin — mark SHIPPED order as DELIVERED")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/delivered")
    public OrderDto markDelivered(@PathVariable Long id) {
        return statusService.markDelivered(id);
    }
}
