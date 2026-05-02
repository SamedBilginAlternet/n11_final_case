package com.n11.auth.api.dto;

import com.n11.auth.domain.AddressType;

public record AddressDto(
        Long id,
        AddressType addressType,
        String title,
        String recipientName,
        String phone,
        String line1,
        String city,
        String district,
        String postalCode,
        boolean defaultAddress
) {}
