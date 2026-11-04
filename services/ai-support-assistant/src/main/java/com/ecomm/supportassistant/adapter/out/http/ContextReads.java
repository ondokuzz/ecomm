package com.ecomm.supportassistant.adapter.out.http;

import com.ecomm.commons.money.Money;
import com.ecomm.supportassistant.application.port.out.Lookup;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** How each context's client reads: one request, its answer as a {@link Lookup}. */
final class ContextReads {

  private ContextReads() {}

  /** An amount as the contexts answer it, in the Currency's minor unit. */
  record MoneyBody(long amountMinor, String currency) {
    Money toMoney() {
      return Money.of(amountMinor, currency);
    }
  }

  /**
   * The answer to {@code request} from {@code context}: not found on a 404; failed on any other
   * status, on no answer in time or at all, or on a body that can't be read.
   */
  static <B, T> Lookup<T> lookup(
      String context,
      RestClient.RequestHeadersSpec<?> request,
      ParameterizedTypeReference<B> body,
      Function<B, T> view) {
    try {
      return request.exchange(
          (req, response) -> {
            if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
              return new Lookup.NotFound<>();
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
              return new Lookup.Failed<>(context + " answered " + response.getStatusCode().value());
            }
            var read = response.bodyTo(body);
            if (read == null) {
              return new Lookup.Failed<>(context + " answered nothing");
            }
            try {
              return new Lookup.Found<>(view.apply(read));
            } catch (RuntimeException e) {
              // A field the read promises is missing.
              return new Lookup.Failed<>(context + " answered what can't be read");
            }
          });
    } catch (RestClientException e) {
      return new Lookup.Failed<>(context + " couldn't be reached");
    }
  }
}
