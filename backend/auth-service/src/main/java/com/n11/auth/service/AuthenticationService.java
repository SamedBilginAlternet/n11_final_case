package com.n11.auth.service;

import com.n11.auth.api.dto.AuthTokenResponse;
import com.n11.auth.api.dto.LoginRequest;
import com.n11.auth.api.mapper.UserMapper;
import com.n11.auth.domain.User;
import com.n11.auth.repository.UserRepository;
import com.n11.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;

    @Transactional
    public IssuedTokens login(LoginRequest request, String userAgent, String ip) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("User disabled");
        }
        if (user.getPasswordHash() == null) {
            throw new BadCredentialsException("Use social login for this account");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return issueTokens(user, userAgent, ip);
    }

    @Transactional
    public IssuedTokens refresh(String presentedRefreshToken, String userAgent, String ip) {
        RefreshTokenService.RotateResult rotated =
                refreshTokenService.rotate(presentedRefreshToken, userAgent, ip);
        JwtTokenProvider.IssuedToken access = tokenProvider.issue(rotated.user());

        log.info("Rotated refresh token for userId={}", rotated.user().getId());
        AuthTokenResponse body = new AuthTokenResponse(
                access.token(), "Bearer", access.expiresInSeconds(),
                access.issuedAt(), userMapper.toDto(rotated.user()));
        return new IssuedTokens(body, rotated.issued().rawToken(), rotated.issued().expiresInSeconds());
    }

    public IssuedTokens issueTokens(User user, String userAgent, String ip) {
        JwtTokenProvider.IssuedToken access = tokenProvider.issue(user);
        RefreshTokenService.Issued refresh = refreshTokenService.issueNewFamily(user, userAgent, ip);

        log.info("Issued JWT + refresh for userId={} accessTtl={}s refreshTtl={}s",
                user.getId(), access.expiresInSeconds(), refresh.expiresInSeconds());

        AuthTokenResponse body = new AuthTokenResponse(
                access.token(), "Bearer", access.expiresInSeconds(),
                access.issuedAt(), userMapper.toDto(user));
        return new IssuedTokens(body, refresh.rawToken(), refresh.expiresInSeconds());
    }

    /**
     * Bundle returned to the controller / OAuth handler.  The body goes into
     * the JSON response or URL fragment; the raw refresh token is meant to
     * be set as an HttpOnly cookie on the same response and never serialised
     * elsewhere.
     */
    public record IssuedTokens(AuthTokenResponse body, String refreshTokenRaw, long refreshTtlSeconds) {}
}
