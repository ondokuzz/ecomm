package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.out.OrderReturnActivities;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Order Management's {@code PATCH /orders/{id}/status} to {@code RETURNED}, naming the Customer.
 */
@Component("orderReturnActivities")
class OrderReturnHttpActivities implements OrderReturnActivities {

  private final RestClient http;

  OrderReturnHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.orderManagementUrl());
  }

  private record StatusChangeBody(String customerId, String status) {}

  @Override
  public void markOrderReturned(String customerId, String orderId) {
    Steps.run(
        "markOrderReturned",
        () ->
            http.patch()
                .uri("/orders/{id}/status", orderId)
                .header("Idempotency-Key", Steps.idempotencyKey("markOrderReturned"))
                .body(new StatusChangeBody(customerId, "RETURNED"))
                .retrieve()
                .toBodilessEntity());
  }
}
