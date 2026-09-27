package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.out.CatalogPort;
import com.ecomm.commons.money.Money;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Catalog's public {@code /products/{sku}}. Until multi-Variant Products arrive, a Variant ID is
 * its Product's SKU, so the Variant is looked up through the Product it names.
 */
@Component
class CatalogClient implements CatalogPort {

  private final RestClient http;

  CatalogClient(@Qualifier("catalogRestClient") RestClient http) {
    this.http = http;
  }

  private record ProductBody(List<VariantBody> variants) {}

  private record VariantBody(String id, PriceBody price) {}

  private record PriceBody(long amountMinor, String currency) {}

  @Override
  public Optional<Money> price(String variantId) {
    return Downstream.call("Catalog", () -> product(variantId)).stream()
        .flatMap(p -> p.variants() == null ? Stream.empty() : p.variants().stream())
        .filter(v -> variantId.equals(v.id()) && v.price() != null)
        .findFirst()
        .map(v -> Money.of(v.price().amountMinor(), v.price().currency()));
  }

  /** The Product whose SKU is {@code sku}; empty on a 404. */
  private Optional<ProductBody> product(String sku) {
    return http.get()
        .uri("/products/{sku}", sku)
        .exchange(
            (request, response) -> {
              if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
              }
              if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException(
                    "Catalog answered " + response.getStatusCode().value() + " for " + sku);
              }
              return Optional.ofNullable(response.bodyTo(ProductBody.class));
            });
  }
}
