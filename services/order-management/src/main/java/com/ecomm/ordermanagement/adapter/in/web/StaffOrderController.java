package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.ordermanagement.application.port.in.BrowseOrdersUseCase;
import com.ecomm.ordermanagement.application.port.in.OrderFilter;
import com.ecomm.ordermanagement.application.port.in.Page;
import com.ecomm.ordermanagement.domain.InvalidOrderException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff read every Customer's Orders with a {@code STAFF} token. Every endpoint here is a read:
 * Staff can't change an Order.
 */
@RestController
@RequestMapping("/staff/orders")
@PreAuthorize("hasRole('STAFF')")
class StaffOrderController {

  private static final Pattern ID_PREFIX = Pattern.compile("[0-9a-fA-F-]{1,36}");

  private final BrowseOrdersUseCase browse;

  StaffOrderController(BrowseOrdersUseCase browse) {
    this.browse = browse;
  }

  /**
   * A page of every Customer's Orders, newest first, with how many there are in all: {@code page}
   * counts from 0, and {@code size} is 1 to {@value PageRequests#MAX_PAGE_SIZE}.
   */
  @GetMapping
  Page<OrderResponse> orders(
      @RequestParam(required = false) String customerId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String placedFrom,
      @RequestParam(required = false) String placedTo,
      @RequestParam(required = false) String idPrefix,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PageRequests.check(page, size);
    return browse
        .orders(
            new OrderFilter(
                customerId,
                status == null ? null : OrderStatuses.from(status),
                instant("placedFrom", placedFrom),
                instant("placedTo", placedTo),
                idPrefix(idPrefix)),
            page,
            size)
        .map(OrderResponse::of);
  }

  /** The start of an Order ID in lower case, as the database writes a UUID; null for none. */
  private static String idPrefix(String value) {
    if (value == null) {
      return null;
    }
    if (!ID_PREFIX.matcher(value).matches()) {
      throw new InvalidFilterException(
          "idPrefix must be the start of an Order ID: 1 to 36 hex digits and hyphens");
    }
    return value.toLowerCase(Locale.ROOT);
  }

  /** The instant a filter names, or null for none. */
  private static Instant instant(String name, String value) {
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw new InvalidFilterException(
          name + " must be an ISO 8601 instant, such as 2026-10-05T00:00:00Z");
    }
  }

  /** The Order with its Customer; 404 for an unknown ID, or one that isn't a UUID. */
  @GetMapping("/{id}")
  OrderResponse order(@PathVariable String id) {
    return OrderIds.parse(id)
        .flatMap(browse::anyOrder)
        .map(OrderResponse::of)
        .orElseThrow(() -> new OrderNotFoundException(id));
  }

  @ExceptionHandler(OrderNotFoundException.class)
  ProblemDetail notFound(OrderNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({
    InvalidOrderException.class,
    InvalidFilterException.class,
    InvalidPageException.class
  })
  ProblemDetail invalid(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
