package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.out.CheckoutSessionActivities;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Checkout's internal {@code POST /checkout/sessions/{id}/end}, naming the Customer. */
@Component("checkoutSessionActivities")
class CheckoutSessionHttpActivities implements CheckoutSessionActivities {

  private final RestClient http;

  CheckoutSessionHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.checkoutPricingUrl());
  }

  private record CustomerBody(String customerId) {}

  @Override
  public void endCheckoutSession(String customerId, String checkoutSessionId) {
    Steps.run(
        "endCheckoutSession",
        () ->
            http.post()
                .uri("/checkout/sessions/{id}/end", checkoutSessionId)
                .body(new CustomerBody(customerId))
                .retrieve()
                .toBodilessEntity());
  }
}
