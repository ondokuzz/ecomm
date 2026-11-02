package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.TransactionHistoryLine;

/** A Customer's Payment transaction history: their Payment transactions across their Payments. */
public interface ReadTransactionHistoryUseCase {

  /** A page of the Customer's Payment transactions the filter matches, newest first. */
  Page<TransactionHistoryLine> transactionHistory(
      TransactionHistoryFilter filter, int page, int size);
}
