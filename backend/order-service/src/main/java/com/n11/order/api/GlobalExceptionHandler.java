package com.n11.order.api;

import com.n11.common.web.BaseExceptionHandler;
import com.n11.order.exception.CartLookupException;
import com.n11.order.exception.EmptyCartException;
import com.n11.order.exception.InsufficientStockException;
import com.n11.order.exception.StockReservationException;
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

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ProblemDetail> insufficientStock(InsufficientStockException ex, HttpServletRequest req) {
        // 409 because the request itself is well-formed; the resource state
        // (stock) just doesn't match what's needed.  productIds list lets the
        // frontend highlight the offending cart line(s).
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        body.setTitle("Insufficient stock");
        body.setDetail("Sepetinizdeki bazı ürünler için yeterli stok yok.");
        body.setProperty("insufficientProductIds", ex.productIds());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(StockReservationException.class)
    public ResponseEntity<ProblemDetail> stockReservation(StockReservationException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), req);
    }
}
