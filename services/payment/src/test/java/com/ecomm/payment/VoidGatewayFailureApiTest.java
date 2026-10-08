package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * A gateway that fails to answer a void is a 502, and nothing is recorded: the Payment stays
 * authorized, so the void can be retried. The mock gateway never fails a void, so this test makes
 * it.
 */
class VoidGatewayFailureApiTest extends PaymentApiTest {

  @MockitoSpyBean PaymentGatewayPort gateway;

  @Test
  void aGatewayFailureOnVoidIsABadGatewayAndRecordsNothing() {
    var payment = authorized("order-void-gateway-error");
    doThrow(new PaymentGatewayUnavailableException("The gateway failed to answer"))
        .when(gateway)
        .voidAuthorization(any(), any());

    voidPayment(payment.id(), "customer-42")
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    var recorded = staffPayments("order-void-gateway-error").getFirst();
    assertThat(recorded.status()).isEqualTo("AUTHORIZED");
    assertThat(recorded.transactions()).hasSize(1);
  }
}
