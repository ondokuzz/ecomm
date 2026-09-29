package com.ecomm.commons.web;

import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Logs one line per call to another service: method, URL without its query, status and duration,
 * plus the Correlation ID from the MDC. A call that gets no response is logged as a warning.
 */
public class OutboundCallLogInterceptor implements ClientHttpRequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(OutboundCallLogInterceptor.class);

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    var target = request.getMethod() + " " + withoutQuery(request.getURI());
    var start = System.nanoTime();
    try {
      var response = execution.execute(request, body);
      log.info("{} -> {} in {} ms", target, response.getStatusCode().value(), millisSince(start));
      return response;
    } catch (IOException | RuntimeException e) {
      log.warn("{} -> failed in {} ms: {}", target, millisSince(start), e.toString());
      throw e;
    }
  }

  private static String withoutQuery(URI uri) {
    return uri.getScheme() + "://" + uri.getAuthority() + uri.getRawPath();
  }

  private static long millisSince(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }
}
