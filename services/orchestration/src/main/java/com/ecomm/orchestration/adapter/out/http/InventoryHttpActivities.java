package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.out.InventoryActivities;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Inventory's {@code /reservations/{id}/commit}. A 409 says the Reservation expired or was released
 * ({@code reservationExpired}, {@code reservationReleased}): an answer, not a failure.
 */
@Component("inventoryActivities")
class InventoryHttpActivities implements InventoryActivities {

  private final RestClient http;

  InventoryHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.inventoryUrl());
  }

  private record CustomerBody(String customerId) {}

  @Override
  public Commitment commitReservation(String customerId, String reservationId) {
    return Steps.call(
        "commitReservation",
        () -> {
          try {
            http.post()
                .uri("/reservations/{id}/commit", reservationId)
                .body(new CustomerBody(customerId))
                .retrieve()
                .toBodilessEntity();
            return Commitment.COMMITTED;
          } catch (HttpClientErrorException.Conflict e) {
            return Commitment.LAPSED;
          }
        });
  }
}
