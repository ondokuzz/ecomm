package com.ecomm.commons.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders authentication and authorization failures as RFC 7807 problem details: 401 and 403 rather
 * than the catch-all 500. It handles both failures thrown inside controllers (method security) and
 * those from the security filter chain, whose entry point and access-denied handler delegate here.
 * It runs ahead of {@code ProblemDetailExceptionHandler}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class SecurityProblemDetailHandler {

  @ExceptionHandler(AuthenticationException.class)
  ProblemDetail handleAuthentication(AuthenticationException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.UNAUTHORIZED, "A valid bearer token is required.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail handleAccessDenied(AccessDeniedException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
  }
}
