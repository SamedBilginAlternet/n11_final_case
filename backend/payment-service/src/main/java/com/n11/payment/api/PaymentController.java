package com.n11.payment.api;

import com.n11.common.security.AuthenticatedUser;
import com.n11.payment.api.dto.PaymentDto;
import com.n11.payment.api.mapper.PaymentMapper;
import com.n11.payment.domain.Payment;
import com.n11.payment.repository.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final PaymentRepository repository;
    private final PaymentMapper mapper;

    @Operation(summary = "List payment attempts for an order owned by the caller (or any order, if ADMIN).")
    @GetMapping("/order/{orderId}")
    public List<PaymentDto> byOrder(@PathVariable Long orderId,
                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        List<Payment> payments = repository.findByOrderIdOrderByCreatedAtAsc(orderId);
        if (payments.isEmpty()) {
            // 404 vs empty 200: empty 200 leaks "this orderId exists for someone else".
            // 404 keeps a non-owner from probing for valid orderIds.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No payments for this order");
        }
        assertOwnerOrAdmin(payments.get(0), caller);
        return mapper.toDtos(payments);
    }

    @Operation(summary = "Get a payment by id (only the owner or an ADMIN).")
    @GetMapping("/{id}")
    public PaymentDto byId(@PathVariable Long id,
                           @AuthenticationPrincipal AuthenticatedUser caller) {
        Payment payment = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        assertOwnerOrAdmin(payment, caller);
        return mapper.toDto(payment);
    }

    private void assertOwnerOrAdmin(Payment payment, AuthenticatedUser caller) {
        if (caller == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (ROLE_ADMIN.equalsIgnoreCase(caller.role())) return;
        if (!payment.getUserId().equals(caller.userId())) {
            // 404 not 403 — don't confirm the resource exists to a non-owner.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }
    }
}
