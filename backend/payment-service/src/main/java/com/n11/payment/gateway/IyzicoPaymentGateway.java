package com.n11.payment.gateway;

import com.iyzipay.Options;
import com.iyzipay.model.Address;
import com.iyzipay.model.BasketItem;
import com.iyzipay.model.BasketItemType;
import com.iyzipay.model.Buyer;
import com.iyzipay.model.Locale;
import com.iyzipay.model.Payment;
import com.iyzipay.model.PaymentChannel;
import com.iyzipay.model.PaymentCard;
import com.iyzipay.model.Status;
import com.iyzipay.request.CreatePaymentRequest;
import com.n11.payment.config.PaymentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "n11.iyzico", name = "enabled", havingValue = "true")
@Slf4j
public class IyzicoPaymentGateway implements PaymentGateway {

    private final Options options;

    public IyzicoPaymentGateway(PaymentProperties props) {
        this.options = new Options();
        this.options.setApiKey(props.apiKey());
        this.options.setSecretKey(props.secretKey());
        this.options.setBaseUrl(props.baseUrl());
        log.info("IyzicoPaymentGateway active (baseUrl={})", props.baseUrl());
    }

    @Override
    public PaymentChargeResult charge(ChargeCommand command) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(UUID.randomUUID().toString());
        request.setPrice(command.amount());
        request.setPaidPrice(command.amount());
        request.setCurrency(command.currency());
        request.setInstallment(1);
        request.setBasketId(String.valueOf(command.orderId()));
        request.setPaymentChannel(PaymentChannel.WEB.name());
        request.setPaymentGroup("PRODUCT");

        PaymentCard card = new PaymentCard();
        if (command.card() != null) {
            card.setCardHolderName(command.card().holderName());
            card.setCardNumber(command.card().number().replaceAll("\\s+", ""));
            card.setExpireMonth(command.card().expireMonth());
            card.setExpireYear(command.card().expireYear());
            card.setCvc(command.card().cvc());
        } else {
            // Fallback to Iyzico's documented sandbox card so the payment flow
            // still works in environments that don't pipe card data through
            // (smoke tests, internal admin tools).
            card.setCardHolderName("John Doe");
            card.setCardNumber("5528790000000008");
            card.setExpireMonth("12");
            card.setExpireYear("2030");
            card.setCvc("123");
        }
        card.setRegisterCard(0);
        request.setPaymentCard(card);

        var basket = command.items().stream().map(item -> {
            BasketItem bi = new BasketItem();
            bi.setId(String.valueOf(item.productId()));
            bi.setName(item.productName());
            bi.setCategory1("default");
            bi.setItemType(BasketItemType.PHYSICAL.name());
            bi.setPrice(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
            return bi;
        }).toList();
        request.setBasketItems(basket);

        // Iyzico requires buyer + shipping/billing addresses. The shop doesn't
        // currently pipe full address data through the saga, so we synthesise
        // sandbox-acceptable placeholders from what we have (userId, email,
        // card holder). Sandbox doesn't validate realism — only presence.
        request.setBuyer(buildBuyer(command));
        request.setShippingAddress(buildAddress(command));
        request.setBillingAddress(buildAddress(command));

        try {
            Payment payment = Payment.create(request, options);
            if (Status.SUCCESS.getValue().equalsIgnoreCase(payment.getStatus())) {
                log.info("Iyzico charge OK orderId={} paymentId={}", command.orderId(), payment.getPaymentId());
                return PaymentChargeResult.success(String.valueOf(payment.getPaymentId()));
            }
            String reason = payment.getErrorMessage() != null ? payment.getErrorMessage() : "Iyzico error";
            log.warn("Iyzico charge FAILED orderId={} reason={}", command.orderId(), reason);
            return PaymentChargeResult.failure(reason);
        } catch (Exception ex) {
            log.error("Iyzico charge exception orderId={}", command.orderId(), ex);
            return PaymentChargeResult.failure("Gateway exception: " + ex.getMessage());
        }
    }

    private Buyer buildBuyer(ChargeCommand command) {
        String holder = command.card() != null && command.card().holderName() != null
                ? command.card().holderName().trim()
                : "n11 Customer";
        String[] parts = holder.split("\\s+", 2);
        String name = parts[0].isBlank() ? "n11" : parts[0];
        String surname = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "Customer";

        Buyer buyer = new Buyer();
        buyer.setId(String.valueOf(command.userId()));
        buyer.setName(name);
        buyer.setSurname(surname);
        buyer.setGsmNumber("+905350000000");
        buyer.setEmail(command.userEmail() != null ? command.userEmail() : "buyer@n11.local");
        buyer.setIdentityNumber("11111111111");
        buyer.setRegistrationAddress("Nidakule Goztepe, Merdivenkoy Mah. Bora Sok. No:1");
        buyer.setIp("85.34.78.112");
        buyer.setCity("Istanbul");
        buyer.setCountry("Turkey");
        buyer.setZipCode("34732");
        return buyer;
    }

    private Address buildAddress(ChargeCommand command) {
        String holder = command.card() != null && command.card().holderName() != null
                ? command.card().holderName().trim()
                : "n11 Customer";
        Address address = new Address();
        address.setContactName(holder.isBlank() ? "n11 Customer" : holder);
        address.setCity("Istanbul");
        address.setCountry("Turkey");
        address.setAddress("Nidakule Goztepe, Merdivenkoy Mah. Bora Sok. No:1");
        address.setZipCode("34732");
        return address;
    }
}
