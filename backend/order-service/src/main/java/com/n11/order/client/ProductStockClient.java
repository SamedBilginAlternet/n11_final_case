package com.n11.order.client;

import com.n11.order.config.OrderProperties;
import com.n11.order.exception.StockReservationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Service-to-service client for product-service's stock reserve / release
 * endpoints.  Travels via the docker network with a shared
 * X-Internal-Token, never on a user's behalf — there is no end-user JWT
 * forwarded here.
 */
@Component
@Slf4j
public class ProductStockClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;
    private final String token;

    public ProductStockClient(OrderProperties props) {
        this.token = props.internal() == null ? null : props.internal().apiToken();
        this.client = RestClient.builder()
                .baseUrl(props.services().productBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(READ_TIMEOUT)))
                .build();
    }

    public record ReservationItem(Long productId, int quantity) {}
    public record ReservationResponse(boolean ok, List<Long> insufficientProductIds) {}

    public ReservationResponse reserve(List<ReservationItem> items) {
        return post("/api/products/internal/stock/reserve", items, ReservationResponse.class);
    }

    public void release(List<ReservationItem> items) {
        post("/api/products/internal/stock/release", items, Void.class);
    }

    private <T> T post(String path, List<ReservationItem> items, Class<T> responseType) {
        return client.post()
                .uri(path)
                .header("X-Internal-Token", token == null ? "" : token)
                .body(new RequestPayload(items))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("product-service {} returned {}", path, res.getStatusCode());
                    throw new StockReservationException("stock call failed: " + res.getStatusCode());
                })
                .body(responseType);
    }

    private record RequestPayload(List<ReservationItem> items) {}
}
