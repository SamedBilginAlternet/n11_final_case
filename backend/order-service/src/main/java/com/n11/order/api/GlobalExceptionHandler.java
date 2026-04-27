package com.n11.order.api;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.web.ApiError;
import com.n11.order.exception.CartLookupException;
import com.n11.order.exception.EmptyCartException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<ApiError> empty(EmptyCartException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", ex.getMessage(), req.getRequestURI(), MDC.get(CorrelationId.MDC_KEY)));
    }

    @ExceptionHandler(CartLookupException.class)
    public ResponseEntity<ApiError> cart(CartLookupException ex, HttpServletRequest req) {
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
}
