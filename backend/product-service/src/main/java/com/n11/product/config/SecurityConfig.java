package com.n11.product.config;

import com.n11.common.security.JwtAuthenticationFilter;
import com.n11.common.security.JwtParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Product catalog browsing is public (anonymous reads) but review writes
 * require an authenticated user. The JWT filter populates the security
 * context when a Bearer token is present so the controller's
 * {@code @AuthenticationPrincipal AuthenticatedUser} resolves; without one,
 * GETs still flow through but PUT/DELETE on /reviews fall through to a 403.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public JwtParser jwtParser(JwtProperties props) {
        return new JwtParser(props.secret(), props.issuer());
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtParser jwtParser) {
        return new JwtAuthenticationFilter(jwtParser);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Admin metrics: require auth; @PreAuthorize on controller
                        // gates ADMIN.  Listed BEFORE the public GET matcher so
                        // it isn't shadowed by /api/products/** permitAll.
                        .requestMatchers(HttpMethod.GET,  "/api/products/admin/**").authenticated()
                        // Admin image upload — multipart POST.  Falls under the
                        // same /admin/** ADMIN gate via @PreAuthorize on the
                        // controller; we still demand a valid JWT here so
                        // anonymous POST gets 401, not 403.
                        .requestMatchers(HttpMethod.POST, "/api/products/admin/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/products/*/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*/reviews/**").authenticated()
                        // Admin product CRUD — @PreAuthorize on the controller is
                        // the actual ADMIN gate, but require a valid JWT here too
                        // so unauthenticated requests get 401 (not 403) and the
                        // panel's interceptor can wipe the session cleanly.
                        .requestMatchers(HttpMethod.POST,   "/api/products").authenticated()
                        .requestMatchers(HttpMethod.PUT,    "/api/products/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*").authenticated()
                        .requestMatchers(HttpMethod.POST,   "/api/categories").authenticated()
                        .requestMatchers(HttpMethod.PUT,    "/api/categories/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/*").authenticated()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
