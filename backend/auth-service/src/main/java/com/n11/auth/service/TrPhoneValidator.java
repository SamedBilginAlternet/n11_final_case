package com.n11.auth.service;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class TrPhoneValidator implements ConstraintValidator<ValidTrPhone, String> {

    /**
     * After stripping formatting we want a digit sequence that ends with
     * {@code 5XXXXXXXXX} (10 digits, leading 5).  Optional country code 90
     * and trunk 0 are tolerated up front:
     *   5551234567       → ok
     *   05551234567      → ok
     *   905551234567     → ok
     *   +90 (555) 123 45 67 → ok after digit-strip → 905551234567
     */
    private static final Pattern DIGITS_PATTERN = Pattern.compile("(90)?0?5\\d{9}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isBlank()) return false;
        String digits = value.replaceAll("[^0-9]", "");
        return DIGITS_PATTERN.matcher(digits).matches();
    }
}
