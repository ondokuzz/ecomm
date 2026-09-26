package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A Customer sees their own Cart. */
class ViewCartApiTest extends CartApiTest {

  @Test
  void aCustomerWhoHasAddedNothingHasAnEmptyCart() {
    assertThat(itemsOf("customer-new")).isEmpty();
  }
}
