package com.n11.product.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Service-to-service guard for {@code /api/products/internal/**}.
 *
 * <p>The order-service calls these endpoints (stock reserve / release) over
 * the docker network — there's no end-user JWT to verify, so we use a shared
 * bearer-style token via {@code X-Internal-Token} that both services read
 * from the same env var.  Any external request that reaches the gateway and
 * tries to hit /internal/* (whether by accident or a probe) lacks the header
 * and gets a 403 here — defence in depth, even if the gateway is later
 * misconfigured to expose the path.</p>
 *
 * <p>If the configured token is blank we leave the path open (typical for
 * local/dev) and log a warning at startup.  In prod the env var is required
 * via Infisical.</p>
 */
@Component
@Slf4j
public class InternalApiTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Token";
    private static final String INTERNAL_PATH_PREFIX = "/api/products/internal/";

    private final String expectedToken;

    public InternalApiTokenFilter(@Value("${internal.api-token:}") String token) {
        this.expectedToken = token;
        if (token == null || token.isBlank()) {
            log.warn("internal.api-token is blank — /api/products/internal/* is unguarded "
                    + "(acceptable in dev, NOT in prod)");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)
                || expectedToken == null || expectedToken.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        String presented = request.getHeader(HEADER);
        if (presented == null || !expectedToken.equals(presented)) {
            log.warn("Rejected internal call to {} — missing or invalid {} header",
                    request.getRequestURI(), HEADER);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "internal token required");
            return;
        }
        chain.doFilter(request, response);
    }
}
