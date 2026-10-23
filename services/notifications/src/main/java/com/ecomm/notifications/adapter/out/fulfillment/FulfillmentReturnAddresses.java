package com.ecomm.notifications.adapter.out.fulfillment;

import com.ecomm.notifications.application.port.out.ReturnAddresses;
import com.ecomm.notifications.domain.ReturnAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Reads a Warehouse's Return address from Fulfillment & Shipping ({@code GET /warehouses/{id}})
 * with Notifications' own client-credentials token, which holds the {@code NOTIFICATIONS} realm
 * role. A 404 is a Warehouse that isn't configured; anything else but an answer is Fulfillment &
 * Shipping being unavailable.
 */
@Component
class FulfillmentReturnAddresses implements ReturnAddresses {

  /** The fields of the Warehouse read Notifications needs. */
  record WarehouseResponse(ReturnAddress returnAddress) {}

  private final RestClient fulfillment;

  FulfillmentReturnAddresses(
      RestClient.Builder builder,
      OAuth2ClientHttpRequestInterceptor serviceIdentity,
      @Value("${ecomm.notifications.fulfillment-shipping-url}") String baseUrl,
      @Value("${ecomm.notifications.fulfillment-shipping-timeout}") Duration timeout) {
    var requests =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    requests.setReadTimeout(timeout);
    fulfillment =
        builder
            .clone()
            .baseUrl(baseUrl)
            .requestFactory(requests)
            .requestInterceptor(serviceIdentity)
            .build();
  }

  @Override
  public Optional<ReturnAddress> of(String warehouseId) {
    try {
      var warehouse =
          fulfillment
              .get()
              .uri("/warehouses/{id}", warehouseId)
              .retrieve()
              .body(WarehouseResponse.class);
      return Optional.ofNullable(warehouse).map(WarehouseResponse::returnAddress);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      }
      throw new WarehousesUnavailableException(
          "Fulfillment & Shipping answered " + e.getStatusCode(), e);
    } catch (RuntimeException e) {
      // No answer, a 5xx, or no token to ask with: Spring Security's own exception.
      throw new WarehousesUnavailableException(
          "Fulfillment & Shipping didn't answer: " + e.getMessage(), e);
    }
  }
}
