package com.n11.payment.api;

import com.n11.payment.api.dto.PaymentDto;
import com.n11.payment.api.mapper.PaymentMapper;
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
    private final PaymentMapper mapper;

    @Operation(summary = "List payment attempts for an order (newest last)")
    @GetMapping("/order/{orderId}")
    public List<PaymentDto> byOrder(@PathVariable Long orderId) {
        return mapper.toDtos(repository.findByOrderIdOrderByCreatedAtAsc(orderId));
    }

    @Operation(summary = "Get a payment by id")
    @GetMapping("/{id}")
    public PaymentDto byId(@PathVariable Long id) {
        return repository.findById(id).map(mapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }
}
