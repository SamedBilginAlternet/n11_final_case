package com.n11.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * All fields are optional — null means "leave alone".  The controller
 * applies only the fields the client sent so a profile page can be split
 * into many small forms (just-email, just-name) without a single fat
 * mutation hitting unrelated columns.
 */
public record UpdateProfileRequest(
        @Email @Size(max = 160) String email,
        @Size(max = 160) String fullName
) {}
