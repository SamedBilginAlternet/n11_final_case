package com.n11.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Reusable per-identity token-bucket rate limiter shared across services.
 *
 * <p>Construction</p>
 * <pre>
 *   new TokenBucketRateLimitFilter(
 *       capacity,          // max requests in any rolling window
 *       windowSeconds,     // refill = capacity/windowSeconds tokens/sec
 *       request -> isLogin // pure-function predicate; only requests that
 *                          //   match it consume tokens, everything else
 *                          //   passes through (Swagger, actuator, etc.)
 *   )
 * </pre>
 *
 * <p>Identity precedence — first non-empty wins:</p>
 * <ol>
 *   <li>{@code X-Guest-Token} header (set by frontend per browser session)</li>
 *   <li>{@code X-Forwarded-For} first hop (proxy / Caddy in front)</li>
 *   <li>{@code request.getRemoteAddr()} (direct connection)</li>
 * </ol>
 *
 * <p>Mechanics</p>
 * <ul>
 *   <li>Lazy refill on access — no scheduled task, no Redis dep.</li>
 *   <li>{@code ConcurrentHashMap} backed; per-bucket {@code synchronized}
 *       block so two concurrent requests for the same key serialize their
 *       arithmetic without blocking unrelated keys.</li>
 *   <li>Empty bucket → {@code 429 Too Many Requests} with
 *       {@code Retry-After: <seconds>} header.</li>
 * </ul>
 *
 * <p>Single-process only. For multi-node deployments swap the in-memory
 * {@code Map} for a Redis-backed bucket store — public API stays the same.</p>
 */
@Slf4j
public class TokenBucketRateLimitFilter extends OncePerRequestFilter {

    private final int capacity;
    private final double tokensPerSecond;
    private final Predicate<HttpServletRequest> appliesTo;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimitFilter(int capacity, int windowSeconds,
                                      Predicate<HttpServletRequest> appliesTo) {
        if (capacity <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("capacity and windowSeconds must be > 0");
        }
        this.capacity = capacity;
        this.tokensPerSecond = (double) capacity / windowSeconds;
        this.appliesTo = appliesTo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !appliesTo.test(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = identityFor(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));

        long retryAfterSeconds;
        synchronized (bucket) {
            bucket.refill(tokensPerSecond, capacity);
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                chain.doFilter(request, response);
                return;
            }
            retryAfterSeconds = (long) Math.ceil((1 - bucket.tokens) / tokensPerSecond);
        }

        log.warn("Rate limit exceeded path={} key={} retryAfter={}s",
                request.getRequestURI(), key, retryAfterSeconds);
        response.setStatus(429); // Jakarta Servlet 6 dropped SC_TOO_MANY_REQUESTS — use the literal
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"rate_limit_exceeded\",\"retryAfterSeconds\":" + retryAfterSeconds + "}");
    }

    private static String identityFor(HttpServletRequest request) {
        String guest = request.getHeader("X-Guest-Token");
        if (guest != null && !guest.isBlank()) return "guest:" + guest;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return "ip:" + (comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim());
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos = System.nanoTime();

        Bucket(int initialTokens) {
            this.tokens = initialTokens;
        }

        void refill(double tokensPerSecond, int capacity) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * tokensPerSecond);
            lastRefillNanos = now;
        }
    }
}
