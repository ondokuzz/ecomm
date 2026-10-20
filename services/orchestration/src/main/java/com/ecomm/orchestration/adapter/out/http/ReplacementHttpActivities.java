package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingAddress;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingOption;
import com.ecomm.orchestration.application.port.in.FulfillmentRequest.Line;
import com.ecomm.orchestration.application.port.out.ReplacementActivities;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Fulfillment & Shipping's {@code /shipments} for an RMA's replacement. It goes to the Order
 * Shipment's address with its Shipping option, read by Order. A 409 on creation says the RMA has
 * its replacement already ({@code shipmentExists}), so it is read by RMA, never by Order, which
 * would answer the Order's own Shipment; a 503 ({@code productsNotKnownYet}) is retried like any
 * other failure.
 */
@Component("replacementActivities")
class ReplacementHttpActivities implements ReplacementActivities {

  private final RestClient http;

  ReplacementHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.fulfillmentShippingUrl());
  }

  private record OrderShipmentBody(
      ShippingAddress shippingAddress, ShippingOption shippingOption) {}

  private record CreateBody(
      String kind,
      String rmaId,
      String orderId,
      String customerId,
      List<Line> lines,
      AddressBody shippingAddress,
      ShippingOption shippingOption) {}

  private record ShipmentBody(String id, String status) {}

  @Override
  public Replacement createReplacement(
      String rmaId, String orderId, String customerId, String variantId, int quantity) {
    return Steps.call(
        "createReplacement",
        () -> {
          var order =
              http.get()
                  .uri("/shipments?orderId={orderId}", orderId)
                  .retrieve()
                  .body(OrderShipmentBody.class);
          if (order == null || order.shippingAddress() == null) {
            throw new IllegalStateException(
                "Fulfillment & Shipping answered Order " + orderId + " without its address");
          }
          var body =
              new CreateBody(
                  "REPLACEMENT",
                  rmaId,
                  orderId,
                  customerId,
                  List.of(new Line(variantId, quantity)),
                  AddressBody.of(order.shippingAddress()),
                  order.shippingOption());
          try {
            return replacementFrom(
                rmaId,
                http.post()
                    .uri("/shipments")
                    .header("Idempotency-Key", Steps.idempotencyKey("createReplacement"))
                    .body(body)
                    .retrieve()
                    .body(ShipmentBody.class));
          } catch (HttpClientErrorException.Conflict e) {
            return replacementFrom(rmaId, replacementOf(rmaId));
          }
        });
  }

  @Override
  public Replacement findReplacement(String rmaId) {
    return Steps.call("findReplacement", () -> replacementFrom(rmaId, replacementOf(rmaId)));
  }

  private ShipmentBody replacementOf(String rmaId) {
    return http.get().uri("/shipments?rmaId={rmaId}", rmaId).retrieve().body(ShipmentBody.class);
  }

  private static Replacement replacementFrom(String rmaId, ShipmentBody shipment) {
    if (shipment == null || shipment.id() == null || shipment.status() == null) {
      throw new IllegalStateException(
          "Fulfillment & Shipping answered for RMA "
              + rmaId
              + " without a replacement's ID and status");
    }
    return new Replacement(shipment.id(), shipment.status());
  }

  @Override
  public void dispatchReplacement(String shipmentId) {
    Steps.run(
        "dispatchReplacement",
        () ->
            http.post()
                .uri("/shipments/{id}/dispatch", shipmentId)
                .header("Idempotency-Key", Steps.idempotencyKey("dispatchReplacement"))
                .retrieve()
                .toBodilessEntity());
  }
}
