package com.ecomm.inventory.application.port.out;

import java.util.function.Supplier;

/** Runs work in one database transaction, rolled back if the work throws. */
public interface Transactions {

  <T> T inTransaction(Supplier<T> work);
}
