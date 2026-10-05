package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.ordermanagement.application.port.in.ChangeOrderStatusUseCase;
import com.ecomm.ordermanagement.application.port.in.ConcurrentStatusChangeException;
import com.ecomm.ordermanagement.application.port.in.FindOrdersUseCase;
import com.ecomm.ordermanagement.application.port.in.Page;
import com.ecomm.ordermanagement.application.port.in.PlaceOrderUseCase;
import com.ecomm.ordermanagement.domain.Caller;
import com.ecomm.ordermanagement.domain.IllegalStatusTransitionException;
import com.ecomm.ordermanagement.domain.InvalidOrderException;
import com.ecomm.ordermanagement.domain.Order;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout places a Customer's Orders and moves them along their Order Status with its own {@code
 * CHECKOUT} token, naming the Customer in the body. The Customer reads them with a {@code CUSTOMER}
 * token, whose {@code sub} must own the Order. Staff have no Orders of their own, and read every
 * Customer's through {@link StaffOrderController}.
 */
@RestController
@RequestMapping("/orders")
class OrderController {

  private static final Logger log = LoggerFactory.getLogger(OrderController.class);

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
    var order =
        place.place(
            Caller.CHECKOUT,
            request.toCustomerId(),
            request.toOrderLines(),
            request.toDiscount(),
            request.toTax());
    log.info("Placed Order {}", order.id());
    return ResponseEntity.created(URI.create("/orders/" + order.id()))
        .body(OrderResponse.of(order));
  }

  /**
   * A page of the Customer's Orders, newest first, with how many they have in all: {@code page}
   * counts from 0, and {@code size} is 1 to {@value PageRequests#MAX_PAGE_SIZE}.
   */
  @GetMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  Page<OrderResponse> orders(
      CurrentCustomer customer,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PageRequests.check(page, size);
    return find.orders(customer.id(), page, size).map(OrderResponse::of);
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
    var order =
        ownedOrder(
            id, orderId -> changeStatus.changeStatus(Caller.CHECKOUT, customerId, orderId, next));
    log.info("Moved Order {} to {}", id, next);
    return order;
  }

  /**
   * The Order {@code action} returns for this ID; a 404 if the ID isn't a UUID or it finds none.
   */
  private static OrderResponse ownedOrder(String id, Function<UUID, Optional<Order>> action) {
    return OrderIds.parse(id)
        .flatMap(action)
        .map(OrderResponse::of)
        .orElseThrow(() -> new OrderNotFoundException(id));
  }

  @ExceptionHandler(OrderNotFoundException.class)
  ProblemDetail notFound(OrderNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({IllegalStatusTransitionException.class, ConcurrentStatusChangeException.class})
  ProblemDetail conflict(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler({InvalidOrderException.class, InvalidPageException.class})
  ProblemDetail invalid(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
