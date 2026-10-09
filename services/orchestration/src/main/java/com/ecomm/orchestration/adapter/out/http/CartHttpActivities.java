package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.out.CartActivities;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Cart's internal {@code POST /carts/clear}, naming the Customer. */
@Component("cartActivities")
class CartHttpActivities implements CartActivities {

  private final RestClient http;

  CartHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.cartUrl());
  }

  private record CustomerBody(String customerId) {}

  @Override
  public void clearCart(String customerId) {
    Steps.run(
        "clearCart",
        () ->
            http.post()
                .uri("/carts/clear")
                .body(new CustomerBody(customerId))
                .retrieve()
                .toBodilessEntity());
  }
}
