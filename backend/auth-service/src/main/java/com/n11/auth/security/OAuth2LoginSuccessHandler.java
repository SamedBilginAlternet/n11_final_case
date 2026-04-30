package com.n11.auth.security;

import com.n11.auth.config.SocialLoginProperties;
import com.n11.auth.domain.User;
import com.n11.auth.service.AuthenticationService;
import com.n11.auth.service.AuthenticationService.IssuedTokens;
import com.n11.auth.service.SocialLoginService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SocialLoginService socialLoginService;
    private final AuthenticationService authenticationService;
    private final RefreshCookieFactory cookieFactory;
    private final SocialLoginProperties properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId();
        OAuth2User principal = token.getPrincipal();

        String subject = subjectFor(registrationId, principal);
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");

        if (subject == null || email == null) {
            log.warn("OAuth2 callback for {} missing subject/email — redirecting to failure", registrationId);
            redirectFailure(response, "missing_profile");
            return;
        }

        User user = socialLoginService.upsert(registrationId, subject, email, name);
        IssuedTokens issued = authenticationService.issueTokens(
                user, request.getHeader("User-Agent"), request.getRemoteAddr());

        // Refresh token leaves the auth-service strictly through the HttpOnly
        // cookie — never in the URL where it would land in browser history,
        // referrer headers and any analytics script the SPA loads.  The
        // access token still travels in the URL fragment (not query) so it
        // doesn't hit server access logs and gets stripped on first redirect;
        // the SPA reads it once and keeps it in memory.
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieFactory.issue(issued.refreshTokenRaw(), issued.refreshTtlSeconds()).toString());

        String fragment = "token=" + issued.body().accessToken()
                + "&expiresIn=" + issued.body().expiresIn();

        String redirect = UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                .path(properties.successPath())
                .fragment(fragment)
                .build()
                .toUriString();

        log.info("Social login success userId={} provider={}", user.getId(), registrationId);
        response.sendRedirect(redirect);
    }

    private String subjectFor(String registrationId, OAuth2User principal) {
        Object raw = principal.getAttribute("sub");
        return raw == null ? null : String.valueOf(raw);
    }

    private void redirectFailure(HttpServletResponse response, String code) throws IOException {
        String redirect = UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                .path(properties.failurePath())
                .queryParam("oauth_error", code)
                .build()
                .toUriString();
        response.sendRedirect(redirect);
    }
}
