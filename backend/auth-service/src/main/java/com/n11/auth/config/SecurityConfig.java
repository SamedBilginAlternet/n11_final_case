package com.n11.auth.config;

import com.n11.auth.security.OAuth2LoginFailureHandler;
import com.n11.auth.security.OAuth2LoginSuccessHandler;
import com.n11.common.security.JwtAuthenticationFilter;
import com.n11.common.security.JwtParser;
import com.n11.common.security.TokenBucketRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SocialLoginProperties socialLoginProperties;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Bean
    public JwtParser jwtParser(JwtProperties props) {
        return new JwtParser(props.secret(), props.issuer());
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtParser jwtParser) {
        return new JwtAuthenticationFilter(jwtParser);
    }

    /**
     * Brute-force / credential-stuffing brake on POST /api/auth/login.
     * 10 attempts per IP per minute is generous for a real user (typo, lost
     * password recovery flow) and tight enough that an attacker can't run
     * a meaningful dictionary attack — a 1M-word list at 10/min would take
     * ~190 years per IP.
     *
     * Falls back to register too (POST /api/auth/register) — bots love
     * spinning up throwaway accounts, this caps the rate.
     */
    @Bean
    public TokenBucketRateLimitFilter loginRateLimitFilter() {
        return new TokenBucketRateLimitFilter(10, 60, request ->
                "POST".equals(request.getMethod())
                        && ("/api/auth/login".equals(request.getRequestURI())
                                || "/api/auth/login/phone".equals(request.getRequestURI())
                                || "/api/auth/register".equals(request.getRequestURI())
                                || "/api/auth/refresh".equals(request.getRequestURI())));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           TokenBucketRateLimitFilter loginRateLimitFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/login/phone",
                                "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/oauth2/**").permitAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate-limit BEFORE the JWT filter so attackers can't keep
                // the controller method busy parsing JSON before we 429 them.
                .addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter.class)
                // Without this, enabling oauth2Login wires up the default
                // LoginUrlAuthenticationEntryPoint, which 302-redirects every
                // unauthenticated request to /oauth2/authorization/google.
                // /api/users/me with an expired JWT then returns a redirect
                // (that the SPA can't follow because it's cross-origin to
                // Google) instead of the 401 the axios interceptor needs to
                // trigger /refresh.  Pin a 401 entry point for all API paths.
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        if (socialLoginProperties.anyEnabled()) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(a -> a.baseUri("/api/auth/oauth2/authorize"))
                    .redirectionEndpoint(r -> r.baseUri("/api/auth/oauth2/callback/*"))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler)
            );
        }
        return http.build();
    }
}
