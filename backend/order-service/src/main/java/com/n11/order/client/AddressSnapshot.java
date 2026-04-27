package com.n11.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressSnapshot(
        Long id,
        String recipientName,
        String phone,
        String line1,
        String city,
        String district,
        String postalCode
) {}
