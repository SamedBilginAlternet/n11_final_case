package com.n11.common.web;

import com.n11.common.correlation.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * RFC 9457 Problem Details (Spring 6 {@link ProblemDetail}) base for every
 * service's controller-advice. Subclasses add their own
 * {@code @ExceptionHandler} methods for domain exceptions and reuse
 * {@link #problem(HttpStatus, String, String, HttpServletRequest)} to build
 * a body with our standard extensions:
 *
 * <ul>
 *   <li>{@code correlationId} — request log/trace identifier from MDC</li>
 *   <li>{@code timestamp}    — wall-clock for triage (ISO-8601 UTC)</li>
 *   <li>{@code instance}     — request URI, mirrors RFC 9457 §3.1.5</li>
 * </ul>
 *
 * <p>Validation errors come back with an {@code errors} extension carrying
 * a uniform list of {@code {field, message}} entries — same shape across
 * every service.</p>
 *
 * <p>Spring's parent class already converts {@code ResponseStatusException},
 * {@code ErrorResponseException}, type-mismatch and JSON parse errors to
 * {@code ProblemDetail}; we hook {@link #handleExceptionInternal} to attach
 * our extensions on those too, so output stays uniform.</p>
 */
public abstract class BaseExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseExceptionHandler.class);

    /** Catch-all for anything not handled by a subclass or by the parent. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUncaught(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                        "An unexpected error occurred", req));
    }

    protected ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail == null ? "" : detail);
        pd.setTitle(title);
        if (req != null) pd.setInstance(URI.create(req.getRequestURI()));
        attachExtensions(pd);
        return pd;
    }

    protected ResponseEntity<ProblemDetail> respond(HttpStatus status, String title, String detail,
                                                    HttpServletRequest req) {
        return ResponseEntity.status(status).body(problem(status, title, detail, req));
    }

    protected void attachExtensions(ProblemDetail pd) {
        String cid = MDC.get(CorrelationId.MDC_KEY);
        if (cid != null && pd.getProperties() != null && !pd.getProperties().containsKey("correlationId")) {
            pd.setProperty("correlationId", cid);
        } else if (cid != null && pd.getProperties() == null) {
            pd.setProperty("correlationId", cid);
        }
        if (pd.getProperties() == null || !pd.getProperties().containsKey("timestamp")) {
            pd.setProperty("timestamp", Instant.now().toString());
        }
    }

    /** Uniform validation-error shape across services. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, "Validation failed");
        pd.setTitle("Bad Request");
        attachExtensions(pd);
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.<String, String>of(
                        "field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        pd.setProperty("errors", errors);
        return ResponseEntity.status(status).body(pd);
    }

    /** Decorate every parent-handled exception with our standard extensions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd) {
            attachExtensions(pd);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
