package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.InvalidShippingAddressException;
import com.ecomm.checkoutpricing.domain.ShippingAddress;
import com.ecomm.checkoutpricing.domain.UndeliverableCountryException;

/**
 * Validates a Shipping address as the Customer entered it, kept behind a port so that a real
 * address-validation service can replace the mock (ADR 0005).
 */
public interface AddressValidator {

  /**
   * The address, normalised: trimmed, its inner whitespace collapsed, its country and postcode
   * upper-cased, the postcode in its country's usual form, and blank optional fields null.
   *
   * @throws UndeliverableCountryException when the store doesn't ship to its country, whatever else
   *     is wrong with it
   * @throws InvalidShippingAddressException naming each field that is missing or malformed
   */
  ShippingAddress validate(ShippingAddress address);
}
