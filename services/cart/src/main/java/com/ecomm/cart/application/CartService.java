package com.ecomm.cart.application;

import com.ecomm.cart.application.port.in.ClearCartUseCase;
import com.ecomm.cart.application.port.in.RemoveFromCartUseCase;
import com.ecomm.cart.application.port.in.SetQuantityUseCase;
import com.ecomm.cart.application.port.in.ViewCartUseCase;
import com.ecomm.cart.application.port.out.CartRepository;
import com.ecomm.cart.domain.Cart;
import com.ecomm.cart.domain.CartItem;

public class CartService
    implements ViewCartUseCase, SetQuantityUseCase, RemoveFromCartUseCase, ClearCartUseCase {

  private final CartRepository carts;

  public CartService(CartRepository carts) {
    this.carts = carts;
  }

  @Override
  public Cart cart(String customerId) {
    return carts.find(customerId);
  }

  @Override
  public Cart setQuantity(String customerId, CartItem item) {
    carts.put(customerId, item);
    return carts.find(customerId);
  }

  @Override
  public Cart remove(String customerId, String variantId) {
    carts.remove(customerId, variantId);
    return carts.find(customerId);
  }

  @Override
  public void clear(String customerId) {
    carts.delete(customerId);
  }
}
