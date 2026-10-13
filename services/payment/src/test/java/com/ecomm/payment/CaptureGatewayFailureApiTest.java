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
 * A gateway that fails to answer a capture is a 502, and nothing is recorded: the Payment stays
 * authorized, so the capture can be retried. The mock gateway never fails a capture, so this test
 * makes it.
 */
class CaptureGatewayFailureApiTest extends PaymentApiTest {

  @MockitoSpyBean PaymentGatewayPort gateway;

  @Test
  void aGatewayFailureOnCaptureIsABadGatewayAndRecordsNothing() {
    var payment = authorized("order-capture-gateway-error");
    doThrow(new PaymentGatewayUnavailableException("The gateway failed to answer"))
        .when(gateway)
        .capture(any(), any(), any());

    captureWithKey(payment.id(), "customer-42", "capture-gateway-error-1")
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    var recorded = staffPayments("order-capture-gateway-error").getFirst();
    assertThat(recorded.status()).isEqualTo("AUTHORIZED");
    assertThat(recorded.transactions()).hasSize(1);
  }
}
