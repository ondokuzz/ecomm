package com.ecomm.inventory.application.port.out;

import java.util.function.Supplier;

/** Runs work in one database transaction, rolled back if the work throws. */
public interface Transactions {

  <T> T inTransaction(Supplier<T> work);

  /**
   * Runs read-only work against one snapshot of the database, so every read in it sees the same
   * committed state.
   */
  <T> T inSnapshot(Supplier<T> work);
}
