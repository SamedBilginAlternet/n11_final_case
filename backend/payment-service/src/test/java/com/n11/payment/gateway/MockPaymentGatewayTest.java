package com.n11.payment.gateway;

import com.n11.common.event.OrderItemPayload;
import com.n11.payment.config.PaymentProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    @Test
    void succeedsWhenSimulationDisabled() {
        var props = new PaymentProperties(false, "k", "s", "url",
                new PaymentProperties.Failure(false, 0));
        var gw = new MockPaymentGateway(props);

        var result = gw.charge(new PaymentGateway.ChargeCommand(1L, 1L, "u@x.com",
                new BigDecimal("10.00"), "TRY",
                List.of(new OrderItemPayload(1L, "X", 1, new BigDecimal("10.00"))),
                null, null));

        assertThat(result.success()).isTrue();
        assertThat(result.providerRef()).startsWith("MOCK-");
    }

    @Test
    void rejectsZeroAmount() {
        var props = new PaymentProperties(false, "k", "s", "url",
                new PaymentProperties.Failure(false, 0));
        var gw = new MockPaymentGateway(props);

        var result = gw.charge(new PaymentGateway.ChargeCommand(1L, 1L, "u@x.com",
                BigDecimal.ZERO, "TRY", List.of(), null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo("Amount must be positive");
    }

    @Test
    void alwaysFailsWhenSimulationRateOne() {
        var props = new PaymentProperties(false, "k", "s", "url",
                new PaymentProperties.Failure(true, 1.0));
        var gw = new MockPaymentGateway(props);

        var result = gw.charge(new PaymentGateway.ChargeCommand(1L, 1L, "u@x.com",
                new BigDecimal("10"), "TRY",
                List.of(new OrderItemPayload(1L, "X", 1, new BigDecimal("10"))),
                null, null));

        assertThat(result.success()).isFalse();
    }
}
