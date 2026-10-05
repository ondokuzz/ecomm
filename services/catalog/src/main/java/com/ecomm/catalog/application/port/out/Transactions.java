package com.ecomm.catalog.application.port.out;

import java.util.function.Supplier;

/**
 * Runs work in one multi-document transaction: every write in it, events included, is stored
 * together or not at all. The work is rolled back if it throws, and the exception passes on
 * unchanged. It may run more than once when it conflicts with another transaction, so it must write
 * only through the repositories and the event publisher.
 */
public interface Transactions {

  <T> T inTransaction(Supplier<T> work);
}
