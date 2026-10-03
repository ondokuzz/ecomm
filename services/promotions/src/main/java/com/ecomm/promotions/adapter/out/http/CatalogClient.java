package com.ecomm.promotions.adapter.out.http;

import com.ecomm.promotions.application.port.out.CatalogPort;
import com.ecomm.promotions.domain.CatalogUnavailableException;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Catalog's public {@code /categories} and {@code /currencies}, which need no token. Each is read
 * whenever a Campaign needs checking, since Staff write Campaigns seldom.
 */
@Component
class CatalogClient implements CatalogPort {

  private final RestClient http;

  CatalogClient(RestClient.Builder builder, @Value("${ecomm.promotions.catalog-url}") String url) {
    this.http = builder.clone().baseUrl(url).build();
  }

  private record CategoryBody(String slug) {}

  private record CurrencyBody(String code) {}

  @Override
  public Set<String> categories() {
    return read("/categories", CategoryBody[].class, CategoryBody::slug);
  }

  @Override
  public Set<String> currencies() {
    return read("/currencies", CurrencyBody[].class, CurrencyBody::code);
  }

  private <T> Set<String> read(String path, Class<T[]> type, Function<T, String> key) {
    try {
      var body = http.get().uri(path).retrieve().body(type);
      if (body == null) {
        throw new CatalogUnavailableException("Catalog answered " + path + " with nothing", null);
      }
      return Arrays.stream(body).map(key).collect(Collectors.toUnmodifiableSet());
    } catch (RestClientException e) {
      throw new CatalogUnavailableException("Catalog failed on " + path + ": " + e.getMessage(), e);
    }
  }
}
