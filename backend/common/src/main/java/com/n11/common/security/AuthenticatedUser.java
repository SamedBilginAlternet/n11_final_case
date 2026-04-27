package com.n11.common.security;

public record AuthenticatedUser(Long userId, String email, String role) {}
