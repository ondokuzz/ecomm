package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.out.CatalogPort;
import com.ecomm.commons.money.Money;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Catalog's public {@code /variants/{variantId}}, which prices one Variant. */
@Component
class CatalogClient implements CatalogPort {

  private final RestClient http;

  CatalogClient(@Qualifier("catalogRestClient") RestClient http) {
    this.http = http;
  }

  private record VariantBody(String id, PriceBody price) {}

  private record PriceBody(long amountMinor, String currency) {}

  @Override
  public Optional<Money> price(String variantId) {
    return Downstream.call("Catalog", () -> variant(variantId))
        .filter(v -> v.price() != null)
        .map(v -> Money.of(v.price().amountMinor(), v.price().currency()));
  }

  /** The Variant with ID {@code variantId}; empty on a 404. */
  private Optional<VariantBody> variant(String variantId) {
    return http.get()
        .uri("/variants/{variantId}", variantId)
        .exchange(
            (request, response) -> {
              if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
              }
              if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException(
                    "Catalog answered " + response.getStatusCode().value() + " for " + variantId);
              }
              return Optional.ofNullable(response.bodyTo(VariantBody.class));
            });
  }
}
