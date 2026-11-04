package com.ecomm.supportassistant.adapter.out.http;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Sends the token of the Customer whose message is being answered, as it came in (ADR 0002). It
 * reads the security context of the request thread, so it only works for calls made while serving
 * that request.
 */
class ForwardCustomerToken implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken customer)) {
      throw new IllegalStateException("No Customer token on this thread to read with");
    }
    request.getHeaders().setBearerAuth(customer.getToken().getTokenValue());
    return execution.execute(request, body);
  }
}
