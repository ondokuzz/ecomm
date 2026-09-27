package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.ordermanagement.application.port.in.ChangeOrderStatusUseCase;
import com.ecomm.ordermanagement.application.port.in.ConcurrentStatusChangeException;
import com.ecomm.ordermanagement.application.port.in.FindOrdersUseCase;
import com.ecomm.ordermanagement.application.port.in.PlaceOrderUseCase;
import com.ecomm.ordermanagement.domain.IllegalStatusTransitionException;
import com.ecomm.ordermanagement.domain.InvalidOrderException;
import com.ecomm.ordermanagement.domain.Order;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout places a Customer's Orders and moves them along their Order Status with its own {@code
 * CHECKOUT} token, naming the Customer in the body. The Customer reads them with a {@code CUSTOMER}
 * token, whose {@code sub} must own the Order; Staff have no Orders.
 */
@RestController
@RequestMapping("/orders")
class OrderController {

  private final PlaceOrderUseCase place;
  private final FindOrdersUseCase find;
  private final ChangeOrderStatusUseCase changeStatus;

  OrderController(
      PlaceOrderUseCase place, FindOrdersUseCase find, ChangeOrderStatusUseCase changeStatus) {
    this.place = place;
    this.find = find;
    this.changeStatus = changeStatus;
  }

  /** 201 with the new Order in {@code PLACED}, and its URL in {@code Location}. */
  @PostMapping
  @PreAuthorize("hasRole('CHECKOUT')")
  ResponseEntity<OrderResponse> place(@RequestBody PlaceOrderRequest request) {
    var order = place.place(request.toCustomerId(), request.toOrderLines());
    return ResponseEntity.created(URI.create("/orders/" + order.id()))
        .body(OrderResponse.of(order));
  }

  /** The Customer's Orders, newest first. */
  @GetMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  List<OrderResponse> orders(CurrentCustomer customer) {
    return find.orders(customer.id()).stream().map(OrderResponse::of).toList();
  }

  /**
   * 404 for an unknown ID, one that isn't a UUID, or another Customer's Order, so the answer never
   * reveals that someone else's Order exists.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('CUSTOMER')")
  OrderResponse order(CurrentCustomer customer, @PathVariable String id) {
    return ownedOrder(id, orderId -> find.order(customer.id(), orderId));
  }

  /**
   * 200 with the Order in its new status; 409 when its current status can't reach that one. 404 for
   * an unknown ID, or when the named Customer doesn't own the Order.
   */
  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('CHECKOUT')")
  OrderResponse changeStatus(@PathVariable String id, @RequestBody StatusChangeRequest request) {
    var customerId = request.toCustomerId();
    var next = request.toStatus();
    return ownedOrder(id, orderId -> changeStatus.changeStatus(customerId, orderId, next));
  }

  /**
   * The Order {@code action} returns for this ID; a 404 if the ID isn't a UUID or it finds none.
   */
  private static OrderResponse ownedOrder(String id, Function<UUID, Optional<Order>> action) {
    return parse(id)
        .flatMap(action)
        .map(OrderResponse::of)
        .orElseThrow(() -> new OrderNotFoundException(id));
  }

  private static Optional<UUID> parse(String id) {
    try {
      return Optional.of(UUID.fromString(id));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  @ExceptionHandler(OrderNotFoundException.class)
  ProblemDetail notFound(OrderNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({IllegalStatusTransitionException.class, ConcurrentStatusChangeException.class})
  ProblemDetail conflict(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(InvalidOrderException.class)
  ProblemDetail invalid(InvalidOrderException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
