package com.n11.product.api;

import com.n11.product.api.dto.StockReservationRequest;
import com.n11.product.api.dto.StockReservationResponse;
import com.n11.product.service.StockReservationService;
import com.n11.product.service.StockReservationService.InsufficientStockException;
import com.n11.product.service.StockReservationService.StockItem;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal saga endpoint — reserves a basket of stock atomically before the
 * order goes to payment.  Lives under /internal/* so the security filter can
 * gate it on a shared service-to-service token instead of user JWT (no end
 * user is logged in on this code path; order-service is the caller).
 *
 * <p>Reserve returns 200 + {ok:true} on success.  On insufficient stock the
 * service rolls back any partial decrement and we translate the sentinel
 * exception to 200 + {ok:false, insufficientProductIds:[...]} so the
 * order-service can render a precise "X is out of stock" error to the user.
 * 4xx would also have worked but plain success-with-flag keeps the client
 * branching simpler — no try/catch on every call.</p>
 */
@RestController
@RequestMapping("/api/products/internal/stock")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockReservationService service;

    @PostMapping("/reserve")
    public ResponseEntity<StockReservationResponse> reserve(@Valid @RequestBody StockReservationRequest req) {
        List<StockItem> items = req.items().stream()
                .map(i -> new StockItem(i.productId(), i.quantity()))
                .toList();
        try {
            service.reserve(items);
            return ResponseEntity.ok(new StockReservationResponse(true, List.of()));
        } catch (InsufficientStockException ex) {
            return ResponseEntity.ok(new StockReservationResponse(false, ex.productIds()));
        }
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@Valid @RequestBody StockReservationRequest req) {
        List<StockItem> items = req.items().stream()
                .map(i -> new StockItem(i.productId(), i.quantity()))
                .toList();
        service.release(items);
        return ResponseEntity.noContent().build();
    }
}
