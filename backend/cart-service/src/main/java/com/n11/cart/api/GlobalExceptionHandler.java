package com.n11.cart.api;

import com.n11.cart.exception.InsufficientStockException;
import com.n11.cart.exception.ProductLookupException;
import com.n11.common.web.BaseExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ProblemDetail> stock(InsufficientStockException ex, HttpServletRequest req) {
        return respond(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(ProductLookupException.class)
    public ResponseEntity<ProblemDetail> product(ProductLookupException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), req);
    }
}
