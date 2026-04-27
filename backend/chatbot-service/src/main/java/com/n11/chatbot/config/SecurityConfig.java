package com.n11.chatbot.config;

import com.n11.common.security.TokenBucketRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Chatbot is intentionally usable by anonymous shoppers — preserves the guest
 * flow seen across the rest of the site (see CartContext / guest cart). The
 * security chain therefore permitAll's every endpoint; abuse protection moves
 * to a per-identity token-bucket filter (common's {@link TokenBucketRateLimitFilter})
 * that caps throughput on POST /api/chat — the LLM-cost endpoint.
 *
 * Adding JWT auth here would break anonymous chat — the case study spec
 * explicitly wants guests to be able to talk to the assistant.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TokenBucketRateLimitFilter chatRateLimitFilter(RateLimitProperties props) {
        return new TokenBucketRateLimitFilter(
                props.capacity(),
                props.windowSeconds(),
                req -> "POST".equals(req.getMethod()) && "/api/chat".equals(req.getRequestURI()));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           TokenBucketRateLimitFilter rateLimitFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
