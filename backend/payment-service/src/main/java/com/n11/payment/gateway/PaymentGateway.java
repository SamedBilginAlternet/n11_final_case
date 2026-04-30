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
            List<OrderItemPayload> items,
            // Nullable: when null the gateway should fall back to its own
            // (sandbox) default card. The mock gateway ignores this entirely.
            CardData card,
            // Nullable: when null Iyzico will reject the charge for missing
            // buyer; the mock gateway ignores it.
            BuyerData buyer
    ) {}

    record CardData(
            String holderName,
            String number,
            String expireMonth,
            String expireYear,
            String cvc
    ) {}

    record BuyerData(
            String recipientName,
            String phone,
            String line1,
            String city,
            String district,
            String postalCode
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
