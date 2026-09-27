package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.out.OrderPort;
import com.ecomm.checkoutpricing.domain.OrderStatus;
import com.ecomm.checkoutpricing.domain.PricedLine;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Order Management's {@code /orders}, called with Checkout's token. Every request names the
 * Customer the Order belongs to.
 */
@Component
class OrderManagementClient implements OrderPort {

  private final RestClient http;

  OrderManagementClient(@Qualifier("orderManagementRestClient") RestClient http) {
    this.http = http;
  }

  private record PlaceOrderBody(String customerId, List<Line> lines) {}

  private record Line(String variantId, int quantity, Amount unitPrice) {}

  private record Amount(long amountMinor, String currency) {}

  private record StatusChangeBody(String customerId, String status) {}

  private record OrderBody(String id) {}

  @Override
  public String place(String customerId, List<PricedLine> lines) {
    var body =
        new PlaceOrderBody(
            customerId,
            lines.stream()
                .map(
                    l ->
                        new Line(
                            l.variantId(),
                            l.quantity(),
                            new Amount(
                                l.unitPrice().amountMinor(),
                                l.unitPrice().currency().getCurrencyCode())))
                .toList());
    var order =
        Downstream.call(
            "Order Management",
            () -> http.post().uri("/orders").body(body).retrieve().body(OrderBody.class));
    if (order == null || order.id() == null) {
      throw new DownstreamFailureException("Order Management placed an Order without an ID", null);
    }
    return order.id();
  }

  @Override
  public void changeStatus(String customerId, String orderId, OrderStatus status) {
    Downstream.run(
        "Order Management",
        () ->
            http.patch()
                .uri("/orders/{id}/status", orderId)
                .body(new StatusChangeBody(customerId, status.name()))
                .retrieve()
                .toBodilessEntity());
  }
}
