package com.ecomm.supportassistant.adapter.out.http;

import com.ecomm.commons.money.Money;
import com.ecomm.supportassistant.application.port.out.Lookup;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Order Management's Customer reads, {@code GET /orders} and {@code GET /orders/{id}}, with the
 * Customer's own token: its ownership check answers another Customer's Order as a 404.
 */
@Component
class OrderManagementClient implements OrderManagementPort {

  private final RestClient http;

  OrderManagementClient(@Qualifier("orderManagementRestClient") RestClient http) {
    this.http = http;
  }

  private record OrderBody(
      String id,
      String status,
      Instant placedAt,
      MoneyBody total,
      List<LineBody> lines,
      List<StatusBody> statusHistory) {

    OrderView toView() {
      return new OrderView(
          id,
          status,
          placedAt,
          total.toMoney(),
          lines == null ? List.of() : lines.stream().map(LineBody::toLine).toList(),
          statusHistory == null
              ? List.of()
              : statusHistory.stream().map(s -> new StatusChange(s.status(), s.at())).toList());
    }
  }

  private record LineBody(String variantId, int quantity, MoneyBody unitPrice) {
    Line toLine() {
      return new Line(variantId, quantity, unitPrice.toMoney());
    }
  }

  private record StatusBody(String status, Instant at) {}

  private record MoneyBody(long amountMinor, String currency) {
    Money toMoney() {
      return Money.of(amountMinor, currency);
    }
  }

  private record PageBody(List<OrderBody> items) {}

  @Override
  public Lookup<List<OrderView>> latestOrders(int limit) {
    return lookup(
        http.get().uri("/orders?page=0&size={size}", limit),
        new ParameterizedTypeReference<PageBody>() {},
        page -> page.items().stream().map(OrderBody::toView).toList());
  }

  @Override
  public Lookup<OrderView> order(String orderId) {
    return lookup(
        http.get().uri("/orders/{id}", orderId),
        new ParameterizedTypeReference<OrderBody>() {},
        OrderBody::toView);
  }

  /**
   * The answer to {@code request}: not found on a 404; failed on any other status, on no answer in
   * time or at all, or on a body that can't be read.
   */
  private static <B, T> Lookup<T> lookup(
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
              return new Lookup.Failed<>(
                  "Order Management answered " + response.getStatusCode().value());
            }
            var read = response.bodyTo(body);
            if (read == null) {
              return new Lookup.Failed<>("Order Management answered nothing");
            }
            try {
              return new Lookup.Found<>(view.apply(read));
            } catch (RuntimeException e) {
              // A field the read promises is missing.
              return new Lookup.Failed<>("Order Management answered what can't be read");
            }
          });
    } catch (RestClientException e) {
      return new Lookup.Failed<>("Order Management couldn't be reached");
    }
  }
}
