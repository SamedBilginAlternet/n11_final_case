package com.n11.auth.service;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint asserting that an AddressRequest's {@code city} is one
 * of Turkey's 81 provinces and, if {@code district} is provided, that it
 * belongs to that province.  Cross-field validation lives at class scope so
 * the two values can be inspected together; field-level annotations couldn't
 * see each other.
 */
@Documented
@Constraint(validatedBy = TrLocationValidator.class)
@Target({ElementType.TYPE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTrLocation {
    String message() default "Geçersiz il/ilçe seçimi";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
