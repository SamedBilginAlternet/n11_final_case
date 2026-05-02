package com.n11.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TrPhoneValidatorTest {

    private final TrPhoneValidator validator = new TrPhoneValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "5551234567",
            "05551234567",
            "905551234567",
            "+905551234567",
            "+90 555 123 45 67",
            "0(555) 123 45 67",
            "0-555-123-4567",
    })
    void acceptsCommonTrMobileSurfaceForms(String phone) {
        assertThat(validator.isValid(phone, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "1234",                  // too short
            "02125551234",           // landline (Istanbul)
            "+15551234567",          // wrong country
            "+9055512345",           // 9 digits after 5
            "abcd",
            "+9006551234567",        // doesn't start with 5
    })
    void rejectsBadPhones(String phone) {
        assertThat(validator.isValid(phone, null)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
