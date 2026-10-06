package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.out.CatalogPort;
import com.ecomm.commons.money.Money;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Catalog's public {@code /variants/{variantId}}, which prices one Variant and names its Product's
 * SKU and Category.
 */
@Component
class CatalogClient implements CatalogPort {

  private final RestClient http;

  CatalogClient(@Qualifier("catalogRestClient") RestClient http) {
    this.http = http;
  }

  private record VariantBody(String id, PriceBody price, ProductBody product) {}

  private record PriceBody(long amountMinor, String currency) {}

  private record ProductBody(String sku, String category) {}

  @Override
  public Optional<PricedVariant> variant(String variantId) {
    var found = Downstream.call("Catalog", () -> fetch(variantId)).filter(v -> v.price() != null);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    var variant = found.get();
    if (variant.product() == null
        || variant.product().sku() == null
        || variant.product().category() == null) {
      throw new DownstreamFailureException(
          "Catalog named no Product for Variant " + variantId, null);
    }
    return Optional.of(
        new PricedVariant(
            variant.product().sku(),
            variant.product().category(),
            Money.of(variant.price().amountMinor(), variant.price().currency())));
  }

  /** The Variant with ID {@code variantId}; empty on a 404. */
  private Optional<VariantBody> fetch(String variantId) {
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
