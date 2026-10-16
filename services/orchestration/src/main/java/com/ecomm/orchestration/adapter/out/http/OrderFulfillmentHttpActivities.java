package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.out.OrderFulfillmentActivities;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Order Management's {@code PATCH /orders/{id}/status} along fulfillment, naming the Customer. */
@Component("orderFulfillmentActivities")
class OrderFulfillmentHttpActivities implements OrderFulfillmentActivities {

  private final RestClient http;

  OrderFulfillmentHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.orderManagementUrl());
  }

  private record StatusChangeBody(String customerId, String status) {}

  @Override
  public void markOrderFulfilled(String customerId, String orderId) {
    changeStatus("markOrderFulfilled", customerId, orderId, "FULFILLED");
  }

  @Override
  public void markOrderShipped(String customerId, String orderId) {
    changeStatus("markOrderShipped", customerId, orderId, "SHIPPED");
  }

  @Override
  public void markOrderDelivered(String customerId, String orderId) {
    changeStatus("markOrderDelivered", customerId, orderId, "DELIVERED");
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
