package com.n11.auth.api.dto;

import com.n11.auth.domain.AddressType;
import com.n11.auth.service.ValidTrLocation;
import com.n11.auth.service.ValidTrPhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidTrLocation
public record AddressRequest(
        @NotNull AddressType addressType,
        @NotBlank @Size(max = 60) String title,
        @NotBlank @Size(max = 120) String recipientName,
        @NotBlank @Size(max = 32) @ValidTrPhone String phone,
        @NotBlank @Size(max = 255) String line1,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 80) String district,
        @Size(max = 16) String postalCode,
        boolean defaultAddress
) {}
