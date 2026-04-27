package com.n11.cart.client;

import com.n11.cart.config.CartProperties;
import com.n11.cart.exception.ProductLookupException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
@Slf4j
public class ProductClient {

    // Without these, RestClient's default JDK request factory has *no* timeout —
    // a hung product-service ties up cart-service threads indefinitely.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;

    public ProductClient(CartProperties props) {
        this.client = RestClient.builder()
                .baseUrl(props.services().productBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(READ_TIMEOUT)))
                .build();
    }

    public ProductSnapshot fetch(Long productId) {
        return client.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 404) {
                        throw new ResponseStatusException(NOT_FOUND, "Product not found: " + productId);
                    }
                    throw new ProductLookupException("Product lookup failed: " + res.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new ProductLookupException("Product service error: " + res.getStatusCode());
                })
                .body(ProductSnapshot.class);
    }
}
