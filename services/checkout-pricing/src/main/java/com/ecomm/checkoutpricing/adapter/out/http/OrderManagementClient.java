package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.out.OrderPort;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.OrderStatus;
import com.ecomm.checkoutpricing.domain.PlacedOrder;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.commons.money.Money;
import com.fasterxml.jackson.annotation.JsonInclude;
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

  /** {@code discount} is left out when the Order has none. */
  private record PlaceOrderBody(
      String customerId,
      List<Line> lines,
      @JsonInclude(JsonInclude.Include.NON_NULL) DiscountBody discount,
      Amount tax) {}

  private record DiscountBody(String couponCode, Amount amount) {}

  private record Line(String variantId, int quantity, Amount unitPrice) {}

  private record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }

    Money toMoney() {
      return Money.of(amountMinor, currency);
    }
  }

  private record StatusChangeBody(String customerId, String status) {}

  private record OrderBody(String id, Amount total) {}

  @Override
  public PlacedOrder place(
      String customerId, List<PricedLine> lines, Discount discount, Money tax) {
    var body =
        new PlaceOrderBody(
            customerId,
            lines.stream()
                .map(l -> new Line(l.variantId(), l.quantity(), Amount.of(l.unitPrice())))
                .toList(),
            discount == null
                ? null
                : new DiscountBody(discount.couponCode(), Amount.of(discount.amount())),
            Amount.of(tax));
    var order =
        Downstream.call(
            "Order Management",
            () -> http.post().uri("/orders").body(body).retrieve().body(OrderBody.class));
    if (order == null || order.id() == null) {
      throw new DownstreamFailureException("Order Management placed an Order without an ID", null);
    }
    if (order.total() == null) {
      throw new DownstreamFailureException(
          "Order Management placed Order " + order.id() + " without a total", null);
    }
    return new PlacedOrder(order.id(), order.total().toMoney());
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
