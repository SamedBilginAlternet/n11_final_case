package com.n11.order.api;

import com.n11.common.web.BaseExceptionHandler;
import com.n11.order.exception.CartLookupException;
import com.n11.order.exception.EmptyCartException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<ProblemDetail> empty(EmptyCartException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(CartLookupException.class)
    public ResponseEntity<ProblemDetail> cart(CartLookupException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), req);
    }
}
