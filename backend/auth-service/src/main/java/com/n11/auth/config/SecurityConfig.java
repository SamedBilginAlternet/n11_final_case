package com.n11.auth.config;

import com.n11.auth.security.GitHubEmailAwareUserService;
import com.n11.auth.security.OAuth2LoginFailureHandler;
import com.n11.auth.security.OAuth2LoginSuccessHandler;
import com.n11.common.security.JwtAuthenticationFilter;
import com.n11.common.security.JwtParser;
import com.n11.common.security.TokenBucketRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SocialLoginProperties socialLoginProperties;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final GitHubEmailAwareUserService gitHubEmailAwareUserService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
                                "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/oauth2/**").permitAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate-limit BEFORE the JWT filter so attackers can't keep
                // the controller method busy parsing JSON before we 429 them.
                .addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter.class);

        if (socialLoginProperties.anyEnabled()) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(a -> a.baseUri("/api/auth/oauth2/authorize"))
                    .redirectionEndpoint(r -> r.baseUri("/api/auth/oauth2/callback/*"))
                    .userInfoEndpoint(u -> u.userService(gitHubEmailAwareUserService))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler)
            );
        }
        return http.build();
    }
}
