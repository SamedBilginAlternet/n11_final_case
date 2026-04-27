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
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
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

        var issued = tokenProvider.issue(user);
        log.info("Issued JWT for userId={} ttl={}s", user.getId(), issued.expiresInSeconds());

        return new AuthTokenResponse(
                issued.token(),
                "Bearer",
                issued.expiresInSeconds(),
                issued.issuedAt(),
                userMapper.toDto(user)
        );
    }
}
