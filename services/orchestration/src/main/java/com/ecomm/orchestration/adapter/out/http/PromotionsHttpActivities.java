package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.out.PromotionsActivities;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Promotions' internal {@code POST /redemptions}, naming the session, Customer and Coupon. */
@Component("promotionsActivities")
class PromotionsHttpActivities implements PromotionsActivities {

  private final RestClient http;

  PromotionsHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.promotionsUrl());
  }

  private record RedemptionBody(String sessionId, String customerId, String couponCode) {}

  @Override
  public void redeemCoupon(String customerId, String checkoutSessionId, String couponCode) {
    Steps.run(
        "redeemCoupon",
        () ->
            http.post()
                .uri("/redemptions")
                .body(new RedemptionBody(checkoutSessionId, customerId, couponCode))
                .retrieve()
                .toBodilessEntity());
  }
}
