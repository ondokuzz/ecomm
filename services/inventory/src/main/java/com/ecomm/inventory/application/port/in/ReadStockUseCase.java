package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.Stock;
import java.util.Optional;

public interface ReadStockUseCase {

  Optional<Stock> stock(String variantId);
}
