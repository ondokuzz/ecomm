package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.Reservation;
import com.ecomm.inventory.domain.StockBatch;
import java.time.Instant;

public interface ReserveStockUseCase {

  /**
   * Holds the whole batch for the Customer until {@code expiresAt}, or nothing: throws {@code
   * UnknownVariantException} or {@code InsufficientStockException} holding no Stock.
   */
  Reservation reserve(String customerId, StockBatch items, Instant expiresAt);
}
