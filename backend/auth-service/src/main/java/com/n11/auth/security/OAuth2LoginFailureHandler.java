package com.n11.auth.security;

import com.n11.auth.config.SocialLoginProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final SocialLoginProperties properties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("OAuth2 login failed: {}", exception.getMessage());
        String message = URLEncoder.encode(
                exception.getMessage() == null ? "oauth_failed" : exception.getMessage(),
                StandardCharsets.UTF_8);
        String redirect = UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                .path(properties.failurePath())
                .queryParam("oauth_error", message)
                .build()
                .toUriString();
        response.sendRedirect(redirect);
    }
}
