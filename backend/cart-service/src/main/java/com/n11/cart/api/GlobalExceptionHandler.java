package com.n11.cart.api;

import com.n11.cart.exception.InsufficientStockException;
import com.n11.cart.exception.ProductLookupException;
import com.n11.common.correlation.CorrelationId;
import com.n11.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> stock(InsufficientStockException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage(), req.getRequestURI(), MDC.get(CorrelationId.MDC_KEY)));
    }

    @ExceptionHandler(ProductLookupException.class)
    public ResponseEntity<ApiError> product(ProductLookupException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of(502, "Bad Gateway", ex.getMessage(), req.getRequestURI(), MDC.get(CorrelationId.MDC_KEY)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> rse(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), ex.getReason(),
                        req.getRequestURI(), MDC.get(CorrelationId.MDC_KEY)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.withDetails(400, "Bad Request", "Validation failed",
                        req.getRequestURI(), MDC.get(CorrelationId.MDC_KEY), details));
    }
}
