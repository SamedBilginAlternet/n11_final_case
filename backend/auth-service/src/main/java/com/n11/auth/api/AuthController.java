package com.n11.auth.api;

import com.n11.auth.api.dto.AuthTokenResponse;
import com.n11.auth.api.dto.LoginRequest;
import com.n11.auth.api.dto.RegisterRequest;
import com.n11.auth.api.dto.UserDto;
import com.n11.auth.service.AuthenticationService;
import com.n11.auth.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody @Valid RegisterRequest request) {
        UserDto dto = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Login with email + password and receive a JWT")
    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }
}
