package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A Customer requests an RMA for a delivered Variant of their Order, and every way it's refused.
 */
class RequestRmaApiTest extends RmasApiTest {

  @Test
  void aReturnOfADeliveredPhoneNamingItsImeiIsAccepted() {
    var phone = newVariant("PHONE");
    var imei = newImei();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 1, "IMEI", imei)
            .delivered(RECENTLY);

    var result = requestedOnceProjected(rma(shipment.orderId(), phone, 1, "RETURN", imei));

    assertThat(result.getStatus().value()).isEqualTo(201);
    var rma = readRma(CUSTOMER, result.getResponseHeaders().getLocation().getPath().substring(6));
    assertThat(rma.reference()).isEqualTo("RMA-" + rma.id().substring(0, 8).toUpperCase());
    assertThat(rma.orderId()).isEqualTo(shipment.orderId());
    assertThat(rma.customerId()).isEqualTo(CUSTOMER_ID);
    assertThat(rma.variantId()).isEqualTo(phone);
    assertThat(rma.productName()).isEqualTo("Galaxy S25");
    assertThat(rma.quantity()).isEqualTo(1);
    assertThat(rma.serialNumbers()).containsExactly(imei);
    assertThat(rma.kind()).isEqualTo("RETURN");
    assertThat(rma.reason()).isEqualTo("It stopped charging");
    assertThat(rma.status()).isEqualTo("REQUESTED");
    assertThat(rma.requestedAt()).isEqualTo("2026-10-15T12:00:00Z");
    assertThat(rma.window())
        .isEqualTo(
            new WindowView("RETURN_PERIOD", "2026-10-12T12:00:00Z", "2026-10-26T12:00:00Z", null));
    assertThat(rma.decisions()).isEmpty();
  }
}
