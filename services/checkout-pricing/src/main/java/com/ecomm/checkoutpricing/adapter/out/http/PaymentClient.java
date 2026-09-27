package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.out.PaymentPort;
import com.ecomm.commons.money.Money;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Payment's {@code POST /payments}, called with Checkout's token for the named Customer. */
@Component
class PaymentClient implements PaymentPort {

  private final RestClient http;

  PaymentClient(@Qualifier("paymentRestClient") RestClient http) {
    this.http = http;
  }

  private record AuthorizeBody(String customerId, String orderId, Amount amount) {}

  private record Amount(long amountMinor, String currency) {}

  @Override
  public void authorize(String customerId, String orderId, Money amount) {
    var body =
        new AuthorizeBody(
            customerId,
            orderId,
            new Amount(amount.amountMinor(), amount.currency().getCurrencyCode()));
    Downstream.run(
        "Payment", () -> http.post().uri("/payments").body(body).retrieve().toBodilessEntity());
  }
}
