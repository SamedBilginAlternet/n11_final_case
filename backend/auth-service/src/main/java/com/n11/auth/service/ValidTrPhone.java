package com.n11.auth.service;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Accepts Turkish mobile numbers in any common surface form: with or without
 * the +90 / 0 prefix, with or without spaces / dashes / parens.  The validator
 * strips formatting and asserts the digit sequence ends in 5XXXXXXXXX (10
 * digits, leading 5).  Landlines are rejected; users can still type a
 * landline as a "secondary" number elsewhere if we ever add that.
 */
@Documented
@Constraint(validatedBy = TrPhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTrPhone {
    String message() default "Geçersiz telefon — TR cep numarası girin (örn. 0555 123 45 67)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
