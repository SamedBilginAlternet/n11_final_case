package com.n11.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CheckoutRequest(
        @NotNull Long addressId,
        @Valid @NotNull CardDetails card
) {
    /**
     * Card fields are passed straight through to the payment gateway and never
     * persisted. Production would integrate Iyzico's checkout-form / 3DS so
     * raw PAN never touches our backend at all — this shortcut exists to keep
     * the demo end-to-end without bringing PCI scope into scope.
     */
    public record CardDetails(
            @NotBlank String holderName,
            @NotBlank @Pattern(regexp = "\\d{12,19}") String number,
            @NotBlank @Pattern(regexp = "(0[1-9]|1[0-2])") String expireMonth,
            @NotBlank @Pattern(regexp = "\\d{4}") String expireYear,
            @NotBlank @Pattern(regexp = "\\d{3,4}") String cvc
    ) {}
}
