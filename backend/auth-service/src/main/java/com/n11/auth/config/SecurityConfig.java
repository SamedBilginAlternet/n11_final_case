package com.n11.auth.config;

import com.n11.auth.security.GitHubEmailAwareUserService;
import com.n11.auth.security.OAuth2LoginFailureHandler;
import com.n11.auth.security.OAuth2LoginSuccessHandler;
import com.n11.common.security.JwtAuthenticationFilter;
import com.n11.common.security.JwtParser;
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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/oauth2/**").permitAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

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
