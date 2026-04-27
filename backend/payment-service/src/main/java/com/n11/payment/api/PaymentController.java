package com.n11.payment.api;

import com.n11.payment.api.dto.PaymentDto;
import com.n11.payment.domain.Payment;
import com.n11.payment.repository.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentRepository repository;

    @Operation(summary = "List payment attempts for an order (newest last)")
    @GetMapping("/order/{orderId}")
    public List<PaymentDto> byOrder(@PathVariable Long orderId) {
        return repository.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(this::toDto).toList();
    }

    @Operation(summary = "Get a payment by id")
    @GetMapping("/{id}")
    public PaymentDto byId(@PathVariable Long id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    private PaymentDto toDto(Payment p) {
        return new PaymentDto(p.getId(), p.getOrderId(), p.getUserId(), p.getStatus(),
                p.getAmount(), p.getCurrency(), p.getProviderRef(), p.getFailureReason(),
                p.getAttempt(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
