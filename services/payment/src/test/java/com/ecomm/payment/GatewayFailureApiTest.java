package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** A gateway that fails to answer is a 502, and no Payment is recorded, approved or declined. */
class GatewayFailureApiTest extends PaymentApiTest {

  @Test
  void aGatewayFailureIsABadGatewayAndRecordsNothing() {
    authorize("order-gateway-error", "tok_gateway_error")
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(staffPayments("order-gateway-error")).isEmpty();
  }
}
