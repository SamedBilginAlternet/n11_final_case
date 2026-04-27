package com.n11.product.config;

import com.n11.common.security.JwtAuthenticationFilter;
import com.n11.common.security.JwtParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/products/*/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*/reviews/**").authenticated()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
