package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.out.ReturnsInventoryActivities;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Inventory's {@code /stock/{variantId}/restocks}, and the {@code /reservations} of an RMA's
 * replacement, owned by {@code rma:<rmaId>}. A 409 on reserving says there is too little Stock
 * ({@code insufficientStock}), and on committing that the Reservation expired or was released:
 * answers, not failures.
 */
@Component("returnsInventoryActivities")
class ReturnsInventoryHttpActivities implements ReturnsInventoryActivities {

  /**
   * How long a replacement's Reservation holds its Stock: it is committed straight after, so this
   * only bounds how long Stock stays held should Orchestration stop in between.
   */
  static final Duration HOLD = Duration.ofMinutes(15);

  private final RestClient http;
  private final Clock clock;

  ReturnsInventoryHttpActivities(ServiceRestClients clients, ServiceUrls urls, Clock clock) {
    this.http = clients.forService(urls.inventoryUrl());
    this.clock = clock;
  }

  private record RestockBody(int quantity, String reference) {}

  private record Item(String variantId, int quantity) {}

  private record ReserveBody(String customerId, String expiresAt, List<Item> items) {}

  private record ReservationBody(String id) {}

  private record OwnerBody(String customerId) {}

  @Override
  public void restock(String variantId, int quantity, String reference) {
    Steps.run(
        "restock",
        () ->
            http.post()
                .uri("/stock/{variantId}/restocks", variantId)
                .header("Idempotency-Key", Steps.idempotencyKey("restock"))
                .body(new RestockBody(quantity, reference))
                .retrieve()
                .toBodilessEntity());
  }

  @Override
  public String reserveReplacement(String rmaId, String variantId, int quantity) {
    var body =
        new ReserveBody(
            ownerOf(rmaId),
            clock.instant().plus(HOLD).toString(),
            List.of(new Item(variantId, quantity)));
    return Steps.call(
        "reserveReplacement",
        () -> {
          try {
            var reservation =
                http.post().uri("/reservations").body(body).retrieve().body(ReservationBody.class);
            if (reservation == null || reservation.id() == null) {
              throw new IllegalStateException(
                  "Inventory answered RMA " + rmaId + "'s Reservation without its ID");
            }
            return reservation.id();
          } catch (HttpClientErrorException.Conflict e) {
            return null;
          }
        });
  }

  @Override
  public boolean commitReplacement(String rmaId, String reservationId) {
    return Steps.call(
        "commitReplacement",
        () -> {
          try {
            http.post()
                .uri("/reservations/{id}/commit", reservationId)
                .body(new OwnerBody(ownerOf(rmaId)))
                .retrieve()
                .toBodilessEntity();
            return true;
          } catch (HttpClientErrorException.Conflict e) {
            return false;
          }
        });
  }

  /** The owner of an RMA's Reservation, as Inventory names it. */
  private static String ownerOf(String rmaId) {
    return "rma:" + rmaId;
  }
}
