package com.n11.common.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless JWT bearer-token filter shared by every service that consumes
 * tokens issued by auth-service.
 *
 * <p>Behaviour</p>
 * <ul>
 *   <li>No {@code Authorization} header → no-op, anonymous request continues
 *       through the chain (downstream {@code authorizeHttpRequests} decides
 *       what's permitted).</li>
 *   <li>Header doesn't start with {@code "Bearer "} → same no-op.</li>
 *   <li>Bearer token present but {@link JwtParser#parse} throws {@link JwtException}
 *       → SecurityContext is cleared and the request continues unauthenticated.
 *       The endpoint's auth rules surface a 401 if needed.</li>
 *   <li>Valid token → {@link AuthenticatedUser} principal is set with a single
 *       {@code ROLE_<role>} authority, mirroring Spring Security's convention.</li>
 * </ul>
 *
 * <p>Not annotated with {@code @Component} on purpose — every consuming
 * service builds its own {@code JwtParser} from its own properties, then
 * registers <em>this</em> filter as a {@code @Bean} in its security config.
 * Keeps the filter ignorant of any service-specific properties class.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtParser parser;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                AuthenticatedUser principal = parser.parse(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException ex) {
                log.debug("JWT validation failed: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
