package com.ecomm.commons.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a Correlation ID: the caller's, if well-formed, or a new one. It is echoed on
 * the response, put in the MDC for the rest of the request, and kept as a request attribute so the
 * problem details rendered for this request can name it.
 */
class CorrelationIdFilter extends OncePerRequestFilter {

  static final String ATTRIBUTE = CorrelationId.class.getName();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var id = CorrelationId.acceptOrGenerate(request.getHeader(CorrelationId.HEADER));
    request.setAttribute(ATTRIBUTE, id);
    response.setHeader(CorrelationId.HEADER, id);
    MDC.put(CorrelationId.MDC_KEY, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
    }
  }
}
