package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.out.PaymentPort;
import com.ecomm.checkoutpricing.domain.PaymentDeclinedException;
import com.ecomm.commons.money.Money;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Payment's {@code POST /payments}, called with Checkout's token for the named Customer and an
 * {@code Idempotency-Key} from the Order's ID, since an Order is authorized once. Payment records a
 * declined payment too, as a 201 with status {@code DECLINED}; a gateway that fails to answer is a
 * 502 from Payment, and so a {@link DownstreamFailureException}.
 */
@Component
class PaymentClient implements PaymentPort {

  private final RestClient http;

  PaymentClient(@Qualifier("paymentRestClient") RestClient http) {
    this.http = http;
  }

  private record AuthorizeBody(
      String customerId, String orderId, String paymentMethod, Amount amount) {}

  private record Amount(long amountMinor, String currency) {}

  private record PaymentBody(String id, String status, String declineReason) {}

  @Override
  public void authorize(String customerId, String orderId, Money amount, String paymentMethod) {
    var body =
        new AuthorizeBody(
            customerId,
            orderId,
            paymentMethod,
            new Amount(amount.amountMinor(), amount.currency().getCurrencyCode()));
    var payment =
        Downstream.call(
            "Payment",
            () ->
                http.post()
                    .uri("/payments")
                    .header("Idempotency-Key", "checkout:" + orderId + ":authorize")
                    .body(body)
                    .retrieve()
                    .body(PaymentBody.class));
    var status = payment == null ? null : payment.status();
    if ("DECLINED".equals(status)) {
      throw new PaymentDeclinedException(payment.declineReason());
    }
    if (!"AUTHORIZED".equals(status)) {
      throw new DownstreamFailureException(
          "Payment answered for Order " + orderId + " with status " + status, null);
    }
  }
}
