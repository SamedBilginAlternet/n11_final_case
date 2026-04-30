package com.n11.product.api;

import com.n11.common.web.BaseExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Product service has no custom domain exceptions to handle yet — controllers
 * throw {@link org.springframework.web.server.ResponseStatusException}. The
 * parent ResponseEntityExceptionHandler converts those to ProblemDetail and
 * {@link BaseExceptionHandler#handleExceptionInternal} attaches our standard
 * extensions, so subclassing alone is enough to opt into RFC 9457 output.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {
}
