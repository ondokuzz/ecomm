package com.ecomm.commons.web;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Sends the current request's Correlation ID on an outbound call, so the downstream service logs
 * under the same one. It reads the MDC of the calling thread; a call made outside a request goes
 * without one, and the downstream service generates its own.
 */
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    CorrelationId.current().ifPresent(id -> request.getHeaders().set(CorrelationId.HEADER, id));
    return execution.execute(request, body);
  }
}
