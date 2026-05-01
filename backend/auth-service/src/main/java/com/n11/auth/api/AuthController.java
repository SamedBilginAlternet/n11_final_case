package com.n11.auth.api;

import com.n11.auth.api.dto.AuthTokenResponse;
import com.n11.auth.api.dto.LoginRequest;
import com.n11.auth.api.dto.PhoneLoginRequest;
import com.n11.auth.api.dto.RegisterRequest;
import com.n11.auth.api.dto.UserDto;
import com.n11.auth.domain.User;
import com.n11.auth.security.RefreshCookieFactory;
import com.n11.auth.service.AuthenticationService;
import com.n11.auth.service.AuthenticationService.IssuedTokens;
import com.n11.auth.service.FirebaseTokenVerifier;
import com.n11.auth.service.FirebaseTokenVerifier.VerifiedPhoneIdentity;
import com.n11.auth.service.PhoneLoginService;
import com.n11.auth.service.RefreshTokenService;
import com.n11.auth.service.RegistrationService;
import org.springframework.beans.factory.ObjectProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, identity")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieFactory cookieFactory;
    private final PhoneLoginService phoneLoginService;
    // FirebaseTokenVerifier is only wired when FIREBASE_SERVICE_ACCOUNT_JSON
    // is set, so the rest of auth-service still boots in dev/CI without it.
    private final ObjectProvider<FirebaseTokenVerifier> firebaseVerifier;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody @Valid RegisterRequest request) {
        UserDto dto = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Login with email + password; access token in body, refresh in HttpOnly cookie")
    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@RequestBody @Valid LoginRequest request,
                                                   HttpServletRequest http) {
        IssuedTokens issued = authenticationService.login(
                request, http.getHeader("User-Agent"), clientIp(http));
        return withRefreshCookie(issued);
    }

    @Operation(summary = "Login with a Firebase ID token from a verified phone number; auto-creates the user on first login.")
    @PostMapping("/login/phone")
    public ResponseEntity<AuthTokenResponse> loginByPhone(@RequestBody @Valid PhoneLoginRequest request,
                                                          HttpServletRequest http) {
        FirebaseTokenVerifier verifier = firebaseVerifier.getIfAvailable();
        if (verifier == null) {
            // Surface as 401 rather than 503 — clients should treat this the
            // same as any other auth failure and fall back to email/Google.
            throw new BadCredentialsException("Phone login is not configured on this server");
        }
        VerifiedPhoneIdentity identity = verifier.verify(request.idToken());
        User user = phoneLoginService.upsertByPhone(identity.phoneNumber());
        IssuedTokens issued = authenticationService.issueTokens(
                user, http.getHeader("User-Agent"), clientIp(http));
        return withRefreshCookie(issued);
    }

    @Operation(summary = "Exchange the HttpOnly refresh-token cookie for a fresh access token + rotated cookie")
    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(
            @CookieValue(name = "${n11.auth.cookie.name:n11_refresh}", required = false) String refreshCookie,
            HttpServletRequest http) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new BadCredentialsException("Missing refresh token");
        }
        IssuedTokens issued = authenticationService.refresh(
                refreshCookie, http.getHeader("User-Agent"), clientIp(http));
        return withRefreshCookie(issued);
    }

    @Operation(summary = "Revoke the current refresh token and clear the cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${n11.auth.cookie.name:n11_refresh}", required = false) String refreshCookie) {
        if (refreshCookie != null && !refreshCookie.isBlank()) {
            refreshTokenService.revoke(refreshCookie);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    private ResponseEntity<AuthTokenResponse> withRefreshCookie(IssuedTokens issued) {
        ResponseCookie cookie = cookieFactory.issue(issued.refreshTokenRaw(), issued.refreshTtlSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(issued.body());
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
