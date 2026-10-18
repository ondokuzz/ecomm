package com.ecomm.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingAddress;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingOption;
import com.ecomm.orchestration.application.port.in.FulfillmentRequest;
import com.ecomm.orchestration.application.port.in.FulfillmentRequest.Line;
import com.ecomm.orchestration.application.port.in.FulfillmentUseCase.OrderChanged;
import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.ecomm.orchestration.application.port.out.FulfillmentWorkflows;
import io.temporal.common.converter.GlobalDataConverter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What a paid Order's fulfillment is started with, and that it reaches the workflow intact. */
class FulfillmentServiceTest {

  final List<FulfillmentRequest> started = new ArrayList<>();

  final FulfillmentWorkflows workflows =
      new FulfillmentWorkflows() {
        @Override
        public boolean start(FulfillmentRequest request) {
          started.add(request);
          return true;
        }

        @Override
        public boolean signal(String orderId, ShipmentChange change) {
          return true;
        }
      };

  @Test
  void aPaidOrdersFulfillmentAwaitsDeliveryForTheWholeDeadline() {
    new FulfillmentService(workflows, Duration.ofMillis(1500), Duration.ofHours(1))
        .orderChanged(paid());

    assertThat(started)
        .singleElement()
        .extracting(FulfillmentRequest::deliveryDeadline)
        .isEqualTo(Duration.ofMillis(1500));
  }

  @Test
  void theRequestReachesTheWorkflowAsItWasStarted() {
    new FulfillmentService(workflows, Duration.ofDays(30), Duration.ofHours(1))
        .orderChanged(paid());
    var request = started.getFirst();

    var converter = GlobalDataConverter.get();
    var payload = converter.toPayload(request).orElseThrow();

    assertThat(converter.fromPayload(payload, FulfillmentRequest.class, FulfillmentRequest.class))
        .isEqualTo(request);
  }

  static OrderChanged paid() {
    return new OrderChanged(
        "order-1",
        "customer-42",
        "PAID",
        List.of(new Line("PHN-PIXEL-9", 1)),
        new ShippingAddress(
            "Erika Mustermann", "Hauptstraße 5", null, "10115", "Berlin", null, "DE", null),
        new ShippingOption("EXPRESS", "Mock Carrier", new Amount(999, "EUR")),
        "corr-1");
  }
}
