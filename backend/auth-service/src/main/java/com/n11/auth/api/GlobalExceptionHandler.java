package com.n11.auth.api;

import com.n11.auth.exception.EmailAlreadyTakenException;
import com.n11.common.web.BaseExceptionHandler;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(EmailAlreadyTakenException.class)
    public ResponseEntity<ProblemDetail> emailTaken(EmailAlreadyTakenException ex, HttpServletRequest req) {
        return respond(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(EntityNotFoundException ex, HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> badCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return respond(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password", req);
    }
}
