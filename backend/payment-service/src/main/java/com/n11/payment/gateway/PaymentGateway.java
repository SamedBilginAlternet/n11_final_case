package com.n11.payment.gateway;

import com.n11.common.event.OrderItemPayload;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentGateway {

    PaymentChargeResult charge(ChargeCommand command);

    record ChargeCommand(
            Long orderId,
            Long userId,
            String userEmail,
            BigDecimal amount,
            String currency,
            List<OrderItemPayload> items
    ) {}

    record PaymentChargeResult(
            boolean success,
            String providerRef,
            String failureReason
    ) {
        public static PaymentChargeResult success(String providerRef) {
            return new PaymentChargeResult(true, providerRef, null);
        }

        public static PaymentChargeResult failure(String reason) {
            return new PaymentChargeResult(false, null, reason);
        }
    }
}
