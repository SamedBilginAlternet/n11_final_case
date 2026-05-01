package com.n11.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneLoginRequest(
        @NotBlank String idToken
) {}
