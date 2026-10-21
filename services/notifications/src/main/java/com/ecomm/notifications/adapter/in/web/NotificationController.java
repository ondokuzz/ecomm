package com.ecomm.notifications.adapter.in.web;

import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.notifications.application.port.in.NotificationFilter;
import com.ecomm.notifications.application.port.in.NotificationFilter.Field;
import com.ecomm.notifications.application.port.in.NotificationReadsUseCase;
import com.ecomm.notifications.application.port.in.Page;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Notification ledger. Staff list the Notifications about an Order or an RMA, or to a Customer;
 * a Customer lists their own. Both are newest first, a page at a time, the address masked.
 */
@RestController
@RequestMapping("/notifications")
class NotificationController {

  /** The most Notifications a page can hold. */
  static final int MAX_PAGE_SIZE = 100;

  private final NotificationReadsUseCase notifications;

  NotificationController(NotificationReadsUseCase notifications) {
    this.notifications = notifications;
  }

  /**
   * Exactly one of {@code orderId}, {@code rmaId} and {@code customerId} names what to list; {@code
   * page} counts from 0, and {@code size} is 1 to {@value #MAX_PAGE_SIZE}.
   */
  @GetMapping
  @PreAuthorize("hasRole('STAFF')")
  Page<NotificationResponse> list(
      @RequestParam(required = false) String orderId,
      @RequestParam(required = false) String rmaId,
      @RequestParam(required = false) String customerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var filters = new ArrayList<NotificationFilter>();
    if (orderId != null) {
      filters.add(new NotificationFilter(Field.ORDER, orderId));
    }
    if (rmaId != null) {
      filters.add(new NotificationFilter(Field.RMA, rmaId));
    }
    if (customerId != null) {
      filters.add(new NotificationFilter(Field.CUSTOMER, customerId));
    }
    if (filters.size() != 1) {
      throw new InvalidRequestException(
          "orderId", "or rmaId or customerId: exactly one of them is required");
    }
    return page(filters.getFirst(), page, size);
  }

  /** The calling Customer's own Notifications. */
  @GetMapping("/me")
  @PreAuthorize("hasRole('CUSTOMER')")
  Page<NotificationResponse> mine(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      CurrentCustomer customer) {
    return page(NotificationFilter.ofCustomer(customer.id()), page, size);
  }

  private Page<NotificationResponse> page(NotificationFilter filter, int page, int size) {
    if (page < 0) {
      throw new InvalidRequestException("page", "counts from 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidRequestException("size", "is 1 to " + MAX_PAGE_SIZE);
    }
    return notifications.notifications(filter, page, size).map(NotificationResponse::of);
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalid(InvalidRequestException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("errors", List.of(e.violation()));
    return problem;
  }
}
