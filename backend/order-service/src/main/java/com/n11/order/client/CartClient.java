package com.n11.order.client;

import com.n11.order.config.OrderProperties;
import com.n11.order.exception.CartLookupException;
import com.n11.order.exception.EmptyCartException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Slf4j
public class CartClient {

    private final RestClient client;

    public CartClient(OrderProperties props) {
        this.client = RestClient.builder().baseUrl(props.services().cartBaseUrl()).build();
    }

    public CartSnapshot fetchCurrent() {
        String authHeader = currentAuthorization();
        CartSnapshot cart = client.get()
                .uri("/api/cart")
                .header("Authorization", authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new CartLookupException("Cart lookup failed: " + res.getStatusCode());
                })
                .body(CartSnapshot.class);
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            throw new EmptyCartException("Cart is empty");
        }
        return cart;
    }

    private String currentAuthorization() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest req = attrs.getRequest();
        String header = req.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            throw new CartLookupException("Missing authorization header");
        }
        return header;
    }
}
