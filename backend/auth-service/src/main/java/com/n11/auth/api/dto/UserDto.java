package com.n11.auth.api.dto;

import com.n11.auth.domain.Role;

import java.time.Instant;

public record UserDto(
        Long id,
        String email,
        String fullName,
        Role role,
        Instant createdAt
) {}
