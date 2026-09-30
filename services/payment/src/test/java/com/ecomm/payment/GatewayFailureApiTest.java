package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/** A gateway that fails to answer is a 502, and no Payment is recorded, approved or declined. */
class GatewayFailureApiTest extends PaymentApiTest {

  @Autowired JdbcClient jdbc;

  @Test
  void aGatewayFailureIsABadGatewayAndRecordsNothing() {
    authorize("order-gateway-error", "tok_gateway_error")
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    var recorded =
        jdbc.sql("SELECT count(*) FROM payment WHERE order_id = 'order-gateway-error'")
            .query(Long.class)
            .single();
    assertThat(recorded).isZero();
  }
}
