package com.n11.auth.api.dto;

public record AddressDto(
        Long id,
        String title,
        String recipientName,
        String phone,
        String line1,
        String city,
        String district,
        String postalCode,
        boolean defaultAddress
) {}
