package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.out.InventoryPort;
import com.ecomm.checkoutpricing.domain.CartLine;
import com.ecomm.checkoutpricing.domain.OutOfStockException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Inventory's {@code /reservations}, called with Checkout's token, naming the Customer each
 * Reservation belongs to. Reserving answers a 409 (not enough Stock) or a 404 (a Variant Inventory
 * doesn't stock) when the Cart can't be sold as it is.
 */
@Component
class InventoryClient implements InventoryPort {

  private final RestClient http;

  InventoryClient(@Qualifier("inventoryRestClient") RestClient http) {
    this.http = http;
  }

  private record ReserveBody(String customerId, Instant expiresAt, List<Item> items) {}

  private record Item(String variantId, int quantity) {}

  private record CustomerBody(String customerId) {}

  private record ReservationBody(String id) {}

  @Override
  public String reserve(String customerId, List<CartLine> lines, Instant expiresAt) {
    var body =
        new ReserveBody(
            customerId,
            expiresAt,
            lines.stream().map(l -> new Item(l.variantId(), l.quantity())).toList());
    var reservation =
        Downstream.call(
            "Inventory",
            () -> {
              try {
                return http.post()
                    .uri("/reservations")
                    .body(body)
                    .retrieve()
                    .body(ReservationBody.class);
              } catch (HttpClientErrorException.Conflict e) {
                throw new OutOfStockException(variantIds(e, "insufficientStock", lines));
              } catch (HttpClientErrorException.NotFound e) {
                throw new OutOfStockException(variantIds(e, "unknownVariants", lines));
              }
            });
    if (reservation == null || reservation.id() == null) {
      throw new DownstreamFailureException("Inventory made a Reservation without an ID", null);
    }
    return reservation.id();
  }

  @Override
  public void release(String customerId, String reservationId) {
    Downstream.run(
        "Inventory",
        () -> {
          try {
            http.post()
                .uri("/reservations/{id}/release", reservationId)
                .body(new CustomerBody(customerId))
                .retrieve()
                .toBodilessEntity();
          } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Conflict e) {
            // Gone, or already committed: either way it holds nothing for this session any more.
          }
        });
  }

  /** The Variants Inventory's problem detail names; all of the Cart's when it names none. */
  private static List<String> variantIds(
      HttpClientErrorException error, String property, List<CartLine> lines) {
    try {
      var problem = error.getResponseBodyAs(Map.class);
      if (problem != null && problem.get(property) instanceof List<?> ids && !ids.isEmpty()) {
        return ids.stream().map(String::valueOf).toList();
      }
    } catch (RuntimeException ignored) {
      // An unreadable body names no Variants.
    }
    return lines.stream().map(CartLine::variantId).toList();
  }
}
