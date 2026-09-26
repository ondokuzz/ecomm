package com.ecomm.inventory.application;

import com.ecomm.inventory.application.port.in.DecrementStockUseCase;
import com.ecomm.inventory.application.port.in.ReadStockUseCase;
import com.ecomm.inventory.application.port.out.StockRepository;
import com.ecomm.inventory.application.port.out.Transactions;
import com.ecomm.inventory.domain.Stock;
import com.ecomm.inventory.domain.StockDecrement;
import java.util.List;
import java.util.Optional;

public class InventoryService implements ReadStockUseCase, DecrementStockUseCase {

  private final StockRepository stock;
  private final Transactions transactions;

  public InventoryService(StockRepository stock, Transactions transactions) {
    this.stock = stock;
    this.transactions = transactions;
  }

  @Override
  public Optional<Stock> stock(String variantId) {
    return stock.find(variantId);
  }

  @Override
  public List<Stock> decrement(StockDecrement decrement) {
    return transactions.inTransaction(
        () -> {
          var decremented = decrement.applyTo(stock.lockAll(decrement.variantIds()));
          stock.updateAll(decremented);
          return decremented;
        });
  }
}
