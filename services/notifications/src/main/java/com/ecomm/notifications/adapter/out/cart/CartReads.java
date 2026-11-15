package com.ecomm.notifications.adapter.out.cart;

import com.ecomm.notifications.application.port.out.Carts;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Reads a named Customer's Cart from Cart's internal read ({@code GET
 * /internal/carts/{customerId}}) with Notifications' own client-credentials token, which holds the
 * {@code NOTIFICATIONS} realm role. A 404 is a Customer with no Cart, or an empty one; anything
 * else but an answer is Cart being unavailable.
 */
@Component
class CartReads implements Carts {

  /** The fields of Cart's read Notifications needs. */
  record CartResponse(List<Item> items, Instant lastChangedAt) {
    record Item(String variantId, int quantity) {}
  }

  private final RestClient cart;

  CartReads(
      RestClient.Builder builder,
      OAuth2ClientHttpRequestInterceptor serviceIdentity,
      @Value("${ecomm.notifications.cart-url}") String baseUrl,
      @Value("${ecomm.notifications.cart-timeout}") Duration timeout) {
    var requests =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    requests.setReadTimeout(timeout);
    cart =
        builder
            .clone()
            .baseUrl(baseUrl)
            .requestFactory(requests)
            .requestInterceptor(serviceIdentity)
            .build();
  }

  @Override
  public Optional<CustomerCart> cartOf(String customerId) {
    try {
      var read =
          cart.get()
              .uri("/internal/carts/{customerId}", customerId)
              .retrieve()
              .body(CartResponse.class);
      if (read == null || read.items() == null || read.items().isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          new CustomerCart(
              read.items().stream()
                  .map(i -> new CustomerCart.Line(i.variantId(), i.quantity()))
                  .toList(),
              read.lastChangedAt()));
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      }
      throw new CartsUnavailableException("Cart answered " + e.getStatusCode(), e);
    } catch (RuntimeException e) {
      // No answer, a 5xx, or no token to ask with: Spring Security's own exception.
      throw new CartsUnavailableException("Cart didn't answer: " + e.getMessage(), e);
    }
  }
}
