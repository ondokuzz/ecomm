package com.ecomm.commons.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Adds the request's Correlation ID to every problem detail as {@code correlationId}, whichever
 * handler rendered it: the shared ones, a service's own, or the security entry point's 401 and 403.
 */
@RestControllerAdvice
class CorrelationIdProblemDetailAdvice implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType contentType,
      Class<? extends HttpMessageConverter<?>> converterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body instanceof ProblemDetail problem
        && request instanceof ServletServerHttpRequest servlet
        && servlet.getServletRequest().getAttribute(CorrelationIdFilter.ATTRIBUTE)
            instanceof String id) {
      problem.setProperty(CorrelationId.MDC_KEY, id);
    }
    return body;
  }
}
