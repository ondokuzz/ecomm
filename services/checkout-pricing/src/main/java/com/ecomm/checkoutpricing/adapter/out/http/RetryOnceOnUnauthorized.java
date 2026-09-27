package com.ecomm.checkoutpricing.adapter.out.http;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Sends a request once more when it gets a 401. It sits outside the OAuth2 interceptor, which drops
 * the rejected token on a 401, so the second attempt carries a fresh one. Retrying is safe even for
 * a {@code POST}: a resource server rejects a bad token before any handler runs. No other status is
 * retried.
 */
class RetryOnceOnUnauthorized implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    var response = execution.execute(request, body);
    if (response.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
      return response;
    }
    response.close();
    return execution.execute(request, body);
  }
}
