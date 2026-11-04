package com.ecomm.supportassistant.adapter.out.http;

import com.ecomm.supportassistant.adapter.out.http.ContextReads.MoneyBody;
import com.ecomm.supportassistant.application.port.out.Lookup;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Order Management's Customer reads, {@code GET /orders}, by Order reference too, and {@code GET
 * /orders/{id}}, with the Customer's own token: its ownership check answers another Customer's
 * Order as a 404, and leaves it out of a list.
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

  private record PageBody(List<OrderBody> items) {}

  @Override
  public Lookup<List<OrderView>> latestOrders(int limit) {
    return ContextReads.lookup(
        "Order Management",
        http.get().uri("/orders?page=0&size={size}", limit),
        new ParameterizedTypeReference<PageBody>() {},
        page -> page.items().stream().map(OrderBody::toView).toList());
  }

  @Override
  public Lookup<List<OrderView>> ordersByReference(String reference, int limit) {
    return ContextReads.lookup(
        "Order Management",
        http.get().uri("/orders?reference={reference}&page=0&size={size}", reference, limit),
        new ParameterizedTypeReference<PageBody>() {},
        page -> page.items().stream().map(OrderBody::toView).toList());
  }

  @Override
  public Lookup<OrderView> order(String orderId) {
    return ContextReads.lookup(
        "Order Management",
        http.get().uri("/orders/{id}", orderId),
        new ParameterizedTypeReference<OrderBody>() {},
        OrderBody::toView);
  }
}
