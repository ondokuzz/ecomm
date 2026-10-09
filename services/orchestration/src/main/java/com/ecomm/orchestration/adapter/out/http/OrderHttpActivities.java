package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Discount;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Line;
import com.ecomm.orchestration.application.port.out.OrderActivities;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Order Management's {@code /orders} commands, naming the Customer in the body. */
@Component("orderActivities")
class OrderHttpActivities implements OrderActivities {

  private final RestClient http;

  OrderHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.orderManagementUrl());
  }

  private record PlaceOrderBody(
      String customerId, List<Line> lines, List<DiscountBody> discounts, Amount tax) {}

  /** A Campaign's names it by ID and name, a Coupon's by its code; the rest is left out. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record DiscountBody(
      String source, String couponCode, String campaignId, String campaignName, Amount amount) {

    static DiscountBody of(Discount d) {
      return new DiscountBody(
          d.source(), d.couponCode(), d.campaignId(), d.campaignName(), d.amount());
    }
  }

  private record OrderBody(String id, Amount total) {}

  private record StatusChangeBody(String customerId, String status) {}

  @Override
  public PlacedOrder placeOrder(
      String customerId, List<Line> lines, List<Discount> discounts, Amount tax) {
    var body =
        new PlaceOrderBody(
            customerId, lines, discounts.stream().map(DiscountBody::of).toList(), tax);
    var order =
        Steps.call(
            "placeOrder",
            () ->
                http.post()
                    .uri("/orders")
                    .header("Idempotency-Key", Steps.idempotencyKey("placeOrder"))
                    .body(body)
                    .retrieve()
                    .body(OrderBody.class));
    if (order == null || order.id() == null || order.total() == null) {
      throw new IllegalStateException("Order Management placed an Order without an ID or total");
    }
    return new PlacedOrder(order.id(), order.total());
  }

  @Override
  public void markOrderPaid(String customerId, String orderId) {
    changeStatus("markOrderPaid", customerId, orderId, "PAID");
  }

  @Override
  public void cancelOrder(String customerId, String orderId) {
    changeStatus("cancelOrder", customerId, orderId, "CANCELLED");
  }

  private void changeStatus(String step, String customerId, String orderId, String status) {
    Steps.run(
        step,
        () ->
            http.patch()
                .uri("/orders/{id}/status", orderId)
                .header("Idempotency-Key", Steps.idempotencyKey(step))
                .body(new StatusChangeBody(customerId, status))
                .retrieve()
                .toBodilessEntity());
  }
}
