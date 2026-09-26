package com.ecomm.inventory.adapter.out.postgres;

import com.ecomm.inventory.application.port.out.Transactions;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** A Postgres transaction around the work; any exception it throws rolls it back. */
@Component
class SpringTransactions implements Transactions {

  private final TransactionTemplate transactionTemplate;

  SpringTransactions(TransactionTemplate transactionTemplate) {
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return transactionTemplate.execute(status -> work.get());
  }
}
