package com.n11.order.client;

import com.n11.order.config.OrderProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Calls auth-service to fetch the user's chosen shipping address at checkout
 * time. The order then snapshots the address fields onto its row, so any
 * later edit/delete in the user's address book leaves the order's shipping
 * data untouched.
 *
 * <p>Forwards the caller's {@code Authorization} header so the auth-service
 * authorisation rules (own-address-only, 404 for foreign access) apply
 * naturally — no service-to-service trust shortcut.</p>
 */
@Component
@Slf4j
public class AddressClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;

    public AddressClient(OrderProperties props) {
        this.client = RestClient.builder()
                .baseUrl(props.services().authBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(READ_TIMEOUT)))
                .build();
    }

    public AddressSnapshot fetch(Long addressId) {
        String authHeader = currentAuthorization();
        return client.get()
                .uri("/api/addresses/{id}", addressId)
                .header("Authorization", authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 404) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Geçersiz adres seçimi");
                    }
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Address lookup failed: " + res.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Auth service error: " + res.getStatusCode());
                })
                .body(AddressSnapshot.class);
    }

    private String currentAuthorization() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest req = attrs.getRequest();
        String header = req.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing authorization header");
        }
        return header;
    }
}
