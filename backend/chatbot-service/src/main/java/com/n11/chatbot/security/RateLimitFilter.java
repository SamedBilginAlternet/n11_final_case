package com.n11.chatbot.security;

import com.n11.chatbot.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token bucket per client identity for the {@code POST /api/chat}
 * endpoint. Stops anonymous abuse from burning Groq / Anthropic quota.
 *
 * <p>Identity precedence — first non-empty wins:</p>
 * <ol>
 *   <li>{@code X-Guest-Token} header (set by frontend per browser session)</li>
 *   <li>{@code X-Forwarded-For} header (Caddy / nginx in front)</li>
 *   <li>{@code request.getRemoteAddr()} (direct connection)</li>
 * </ol>
 *
 * <p>Bucket maths:</p>
 * <ul>
 *   <li>Capacity = max requests in any rolling window.</li>
 *   <li>Refill = capacity / windowSeconds tokens per second, lazily applied
 *       on each access (no scheduled task).</li>
 *   <li>Empty bucket → HTTP 429 with {@code Retry-After} header in seconds
 *       until a single token is replenished.</li>
 * </ul>
 *
 * <p>Only acts on {@code POST /api/chat} — the LLM-cost endpoint. History
 * reads, swagger, actuator pass straight through. Filter is registered before
 * Spring Security's chain (see SecurityConfig).</p>
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CHAT_ENDPOINT = "/api/chat";

    private final int capacity;
    private final int windowSeconds;
    private final double tokensPerSecond;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties props) {
        this.capacity = props.capacity();
        this.windowSeconds = props.windowSeconds();
        this.tokensPerSecond = (double) capacity / windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getMethod().equals("POST") && request.getRequestURI().equals(CHAT_ENDPOINT));
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

        log.warn("Rate-limit exceeded for chat key={} retryAfter={}s", key, retryAfterSeconds);
        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
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
            // first hop = original client
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
