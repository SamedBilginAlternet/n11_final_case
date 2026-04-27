package com.n11.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 60) String title,
        @NotBlank @Size(max = 120) String recipientName,
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(max = 255) String line1,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 80) String district,
        @Size(max = 16) String postalCode,
        boolean defaultAddress
) {}
