package com.ecomm.commons.idempotency;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders a refused {@code Idempotency-Key} as a problem detail with its {@code reason}. It runs
 * ahead of {@code ProblemDetailExceptionHandler}, whose catch-all would make it a 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdempotencyProblemDetailHandler {

  @ExceptionHandler(IdempotencyKeyException.class)
  ProblemDetail handle(IdempotencyKeyException e) {
    var problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    problem.setProperty("reason", e.reason());
    return problem;
  }
}
