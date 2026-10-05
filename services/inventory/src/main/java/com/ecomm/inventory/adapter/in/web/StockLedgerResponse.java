package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.application.port.in.ReadStockMovementsUseCase.StockLedger;
import com.ecomm.inventory.domain.StockMovement;
import java.time.Instant;
import java.util.List;

/**
 * A page of a Variant's Stock movements, newest first, with its On-hand, what its movements add up
 * to, and whether the two agree.
 */
record StockLedgerResponse(
    String variantId,
    int onHand,
    long onHandFromMovements,
    boolean balanced,
    List<MovementResponse> items,
    int page,
    int size,
    long total) {

  /** {@code reservationId} and {@code reason} are null when there is none. */
  record MovementResponse(
      String kind,
      int onHandChange,
      int reservedChange,
      String reservationId,
      String reason,
      Instant at) {

    static MovementResponse of(StockMovement movement) {
      return new MovementResponse(
          movement.kind().name(),
          movement.onHandChange(),
          movement.reservedChange(),
          movement.reservationId() == null ? null : movement.reservationId().toString(),
          movement.reason(),
          movement.at());
    }
  }

  static StockLedgerResponse of(StockLedger ledger) {
    var movements = ledger.movements();
    return new StockLedgerResponse(
        ledger.variantId(),
        ledger.onHand(),
        ledger.onHandFromMovements(),
        ledger.balanced(),
        movements.items().stream().map(MovementResponse::of).toList(),
        movements.page(),
        movements.size(),
        movements.total());
  }
}
