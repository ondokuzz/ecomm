package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.Reservation;
import com.ecomm.inventory.domain.StockBatch;
import java.time.Instant;

public interface ReserveStockUseCase {

  /** The Reservation, and whether this request made it rather than answering an RMA's again. */
  record Reserved(Reservation reservation, boolean created) {}

  /**
   * Holds the whole batch for its owner until {@code expiresAt}, or nothing: throws {@code
   * UnknownVariantException} or {@code InsufficientStockException} holding no Stock. The owner is a
   * Customer's ID, or {@code rma:<rmaId>} for an RMA's replacement. An RMA whose Reservation is
   * committed, or still holds its Stock, gets that one back, and nothing more is held; one that
   * expired uncommitted is released, and the RMA holds Stock anew.
   */
  Reserved reserve(String owner, StockBatch items, Instant expiresAt);
}
