package com.ecomm.cart.adapter.in.web;

import com.ecomm.cart.application.port.in.ClearCartUseCase;
import com.ecomm.cart.application.port.in.RemoveFromCartUseCase;
import com.ecomm.cart.application.port.in.SetQuantityUseCase;
import com.ecomm.cart.application.port.in.ViewCartUseCase;
import com.ecomm.cart.domain.InvalidCartItemException;
import com.ecomm.commons.security.CurrentCustomer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling Customer's own Cart. Every endpoint needs a token with the {@code CUSTOMER} role,
 * whose {@code sub} picks the Cart; Staff have no Cart.
 */
@RestController
@PreAuthorize("hasRole('CUSTOMER')")
@RequestMapping("/cart")
class CartController {

  private final ViewCartUseCase view;
  private final SetQuantityUseCase setQuantity;
  private final RemoveFromCartUseCase remove;
  private final ClearCartUseCase clear;

  CartController(
      ViewCartUseCase view,
      SetQuantityUseCase setQuantity,
      RemoveFromCartUseCase remove,
      ClearCartUseCase clear) {
    this.view = view;
    this.setQuantity = setQuantity;
    this.remove = remove;
    this.clear = clear;
  }

  @GetMapping
  CartResponse cart(CurrentCustomer customer) {
    return CartResponse.of(view.cart(customer.id()));
  }

  @PutMapping("/items/{variantId}")
  CartResponse setQuantity(
      CurrentCustomer customer,
      @PathVariable String variantId,
      @RequestBody SetQuantityRequest request) {
    return CartResponse.of(setQuantity.setQuantity(customer.id(), request.toItem(variantId)));
  }

  @DeleteMapping("/items/{variantId}")
  CartResponse remove(CurrentCustomer customer, @PathVariable String variantId) {
    return CartResponse.of(remove.remove(customer.id(), variantId));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void clear(CurrentCustomer customer) {
    clear.clear(customer.id());
  }

  @ExceptionHandler(InvalidCartItemException.class)
  ProblemDetail invalid(InvalidCartItemException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
