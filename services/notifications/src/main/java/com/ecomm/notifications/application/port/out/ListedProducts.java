package com.ecomm.notifications.application.port.out;

import com.ecomm.commons.money.Money;
import java.util.Map;
import java.util.Optional;

/** The Products Search & Discovery lists now, with each Variant's Price and Stock. */
public interface ListedProducts {

  /** A Variant's Price now, and whether it is in Stock. */
  record ListedVariant(Money price, boolean inStock) {}

  /**
   * The Product's Variants by Variant ID; empty for a Product Search doesn't list, such as one
   * removed from the Catalog.
   *
   * @throws ListingsUnavailableException when Search & Discovery can't answer now
   */
  Optional<Map<String, ListedVariant>> variantsOf(String sku);

  /** Search & Discovery didn't answer; asking again later may work. */
  class ListingsUnavailableException extends RuntimeException {

    public ListingsUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
