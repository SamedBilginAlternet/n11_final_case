package com.n11.payment.gateway;

import com.n11.payment.config.PaymentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(prefix = "n11.iyzico", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    private final PaymentProperties props;

    public MockPaymentGateway(PaymentProperties props) {
        this.props = props;
        log.info("MockPaymentGateway active (n11.iyzico.enabled=false)");
    }

    @Override
    public PaymentChargeResult charge(ChargeCommand command) {
        if (props.failure().simulate()
                && ThreadLocalRandom.current().nextDouble() < props.failure().rate()) {
            return PaymentChargeResult.failure("Simulated random failure");
        }
        if (command.amount().signum() <= 0) {
            return PaymentChargeResult.failure("Amount must be positive");
        }
        String ref = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("MockPaymentGateway charged orderId={} amount={} {} → ref={}",
                command.orderId(), command.amount(), command.currency(), ref);
        return PaymentChargeResult.success(ref);
    }
}
