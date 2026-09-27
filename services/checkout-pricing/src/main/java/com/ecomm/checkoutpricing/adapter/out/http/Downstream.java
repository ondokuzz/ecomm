package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.in.ServiceTokenUnavailableException;
import java.util.function.Supplier;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClientException;

/** Turns what a {@code RestClient} call throws into the ports' exceptions. */
final class Downstream {

  private Downstream() {}

  /**
   * Runs {@code call} against {@code service}. Keycloak failing to issue Checkout's token becomes
   * {@link ServiceTokenUnavailableException}; any other failure {@link DownstreamFailureException}.
   */
  static <T> T call(String service, Supplier<T> call) {
    try {
      return call.get();
    } catch (OAuth2AuthorizationException e) {
      throw new ServiceTokenUnavailableException(e);
    } catch (RestClientException e) {
      throw new DownstreamFailureException(service + " failed: " + e.getMessage(), e);
    }
  }

  static void run(String service, Runnable call) {
    call(
        service,
        () -> {
          call.run();
          return null;
        });
  }
}
