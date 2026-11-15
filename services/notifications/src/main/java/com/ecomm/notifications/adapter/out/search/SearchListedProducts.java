package com.ecomm.notifications.adapter.out.search;

import com.ecomm.commons.money.Money;
import com.ecomm.notifications.application.port.out.ListedProducts;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Reads one Product from Search & Discovery's public read ({@code GET /products/{sku}}), which
 * joins Catalog's Prices with Inventory's Stock, so it needs no token. A 404 is a Product Search
 * doesn't list; anything else but an answer is Search being unavailable.
 */
@Component
class SearchListedProducts implements ListedProducts {

  /** The fields of Search's Product read Notifications needs. */
  record ProductResponse(List<Variant> variants) {
    record Variant(String id, Price price, boolean inStock) {}

    record Price(long amountMinor, String currency) {}
  }

  private final RestClient search;

  SearchListedProducts(
      RestClient.Builder builder,
      @Value("${ecomm.notifications.search-discovery-url}") String baseUrl,
      @Value("${ecomm.notifications.search-discovery-timeout}") Duration timeout) {
    var requests =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    requests.setReadTimeout(timeout);
    search = builder.clone().baseUrl(baseUrl).requestFactory(requests).build();
  }

  @Override
  public Optional<Map<String, ListedVariant>> variantsOf(String sku) {
    try {
      var product = search.get().uri("/products/{sku}", sku).retrieve().body(ProductResponse.class);
      var variants = new HashMap<String, ListedVariant>();
      if (product != null && product.variants() != null) {
        for (var v : product.variants()) {
          variants.put(
              v.id(),
              new ListedVariant(
                  Money.of(v.price().amountMinor(), v.price().currency()), v.inStock()));
        }
      }
      return Optional.of(variants);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      }
      throw new ListingsUnavailableException("Search & Discovery answered " + e.getStatusCode(), e);
    } catch (RuntimeException e) {
      throw new ListingsUnavailableException(
          "Search & Discovery didn't answer: " + e.getMessage(), e);
    }
  }
}
