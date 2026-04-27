package com.n11.auth.api;

import com.n11.auth.api.dto.AuthTokenResponse;
import com.n11.auth.api.dto.LoginRequest;
import com.n11.auth.api.dto.RefreshRequest;
import com.n11.auth.api.dto.RegisterRequest;
import com.n11.auth.api.dto.UserDto;
import com.n11.auth.service.AuthenticationService;
import com.n11.auth.service.RefreshTokenService;
import com.n11.auth.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, identity")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody @Valid RegisterRequest request) {
        UserDto dto = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Login with email + password and receive JWT + refresh token")
    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@RequestBody @Valid LoginRequest request,
                                                   HttpServletRequest http) {
        return ResponseEntity.ok(authenticationService.login(
                request, http.getHeader("User-Agent"), clientIp(http)));
    }

    @Operation(summary = "Exchange a refresh token for a fresh access + rotated refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@RequestBody @Valid RefreshRequest request,
                                                     HttpServletRequest http) {
        return ResponseEntity.ok(authenticationService.refresh(
                request.refreshToken(), http.getHeader("User-Agent"), clientIp(http)));
    }

    @Operation(summary = "Revoke a refresh token (logout for this session)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        if (request != null && request.refreshToken() != null) {
            refreshTokenService.revoke(request.refreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
