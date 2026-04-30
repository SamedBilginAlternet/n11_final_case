package com.n11.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hosts the {@link PasswordEncoder} bean separately from {@code SecurityConfig}
 * to break a constructor-injection cycle:
 *
 * <pre>
 *   SecurityConfig
 *     → OAuth2LoginSuccessHandler (ctor dep)
 *     → AuthenticationService     (ctor dep)
 *     → PasswordEncoder           (was a @Bean inside SecurityConfig)
 * </pre>
 *
 * Spring resolves @Bean factory methods by instantiating their containing
 * @Configuration class first; AuthenticationService asking for PasswordEncoder
 * therefore re-entered SecurityConfig while it was still being built, which
 * Spring (rightly) rejects. Splitting the bean into its own dependency-free
 * config eliminates the back-edge.
 */
@Configuration
public class CryptoConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
