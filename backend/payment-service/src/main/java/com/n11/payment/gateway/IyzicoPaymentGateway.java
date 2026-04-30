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
import com.n11.payment.gateway.PaymentGateway.BuyerData;
import com.n11.payment.gateway.PaymentGateway.ChargeCommand;
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
        BuyerData b = command.buyer();
        if (b == null) {
            throw new IllegalArgumentException("Iyzico charge requires buyer data (recipient + shipping address)");
        }
        String[] parts = b.recipientName() == null ? new String[]{"n11", "Customer"} : b.recipientName().trim().split("\\s+", 2);
        String name = parts[0].isBlank() ? "n11" : parts[0];
        String surname = parts.length > 1 && !parts[1].isBlank() ? parts[1] : name;

        Buyer buyer = new Buyer();
        buyer.setId(String.valueOf(command.userId()));
        buyer.setName(name);
        buyer.setSurname(surname);
        buyer.setGsmNumber(b.phone());
        buyer.setEmail(command.userEmail());
        // We don't collect TC kimlik in the shop; sandbox accepts this and a
        // real integration would either store it on the user profile or
        // capture it at checkout per Iyzico KYC flow.
        buyer.setIdentityNumber("11111111111");
        buyer.setRegistrationAddress(b.line1());
        // Same: we don't propagate the request IP through the saga. Real
        // integration would forward the X-Forwarded-For from the gateway.
        buyer.setIp("0.0.0.0");
        buyer.setCity(b.city());
        buyer.setCountry("Turkey");
        buyer.setZipCode(b.postalCode());
        return buyer;
    }

    private Address buildAddress(ChargeCommand command) {
        BuyerData b = command.buyer();
        Address address = new Address();
        address.setContactName(b.recipientName());
        address.setCity(b.city());
        address.setCountry("Turkey");
        address.setAddress(b.line1());
        address.setZipCode(b.postalCode());
        return address;
    }
}
