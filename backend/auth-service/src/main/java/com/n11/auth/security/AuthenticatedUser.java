package com.n11.auth.security;

public record AuthenticatedUser(Long userId, String email, String role) {}
