package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingAddress;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingOption;
import com.ecomm.orchestration.application.port.in.FulfillmentRequest.Line;
import com.ecomm.orchestration.application.port.out.ShipmentActivities;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Fulfillment & Shipping's {@code /shipments} commands. A 409 on creation says the Order's Shipment
 * exists already ({@code shipmentExists}), so it is read by Order and taken as created; a 503
 * ({@code productsNotKnownYet}) is retried like any other failure.
 */
@Component("shipmentActivities")
class ShipmentHttpActivities implements ShipmentActivities {

  private final RestClient http;

  ShipmentHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.fulfillmentShippingUrl());
  }

  private record CreateBody(
      String orderId,
      String customerId,
      List<Line> lines,
      AddressBody shippingAddress,
      ShippingOption shippingOption) {}

  private record ShipmentBody(String id) {}

  private record HoldBody(String reason) {}

  @Override
  public String createShipment(
      String orderId,
      String customerId,
      List<Line> lines,
      ShippingAddress shippingAddress,
      ShippingOption shippingOption) {
    var body =
        new CreateBody(orderId, customerId, lines, AddressBody.of(shippingAddress), shippingOption);
    var shipment =
        Steps.call(
            "createShipment",
            () -> {
              try {
                return http.post()
                    .uri("/shipments")
                    .header("Idempotency-Key", Steps.idempotencyKey("createShipment"))
                    .body(body)
                    .retrieve()
                    .body(ShipmentBody.class);
              } catch (HttpClientErrorException.Conflict e) {
                return http.get()
                    .uri("/shipments?orderId={orderId}", orderId)
                    .retrieve()
                    .body(ShipmentBody.class);
              }
            });
    if (shipment == null || shipment.id() == null) {
      throw new IllegalStateException(
          "Fulfillment & Shipping answered for Order " + orderId + " without a Shipment ID");
    }
    return shipment.id();
  }

  @Override
  public void dispatchShipment(String shipmentId) {
    Steps.run(
        "dispatchShipment",
        () ->
            http.post()
                .uri("/shipments/{id}/dispatch", shipmentId)
                .header("Idempotency-Key", Steps.idempotencyKey("dispatchShipment"))
                .retrieve()
                .toBodilessEntity());
  }

  @Override
  public void holdShipment(String shipmentId, String reason) {
    Steps.run(
        "holdShipment",
        () ->
            http.post()
                .uri("/shipments/{id}/hold", shipmentId)
                .header("Idempotency-Key", Steps.idempotencyKey("holdShipment"))
                .body(new HoldBody(reason))
                .retrieve()
                .toBodilessEntity());
  }
}
