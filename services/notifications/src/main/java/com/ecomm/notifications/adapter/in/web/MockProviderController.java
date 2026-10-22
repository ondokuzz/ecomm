package com.ecomm.notifications.adapter.in.web;

import com.ecomm.notifications.application.port.in.MockProviderReadsUseCase;
import com.ecomm.notifications.application.port.in.MockedMessage;
import com.ecomm.notifications.application.port.in.Page;
import com.ecomm.notifications.domain.NotificationChannel;
import java.time.Instant;
import java.util.List;
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
 * What the mock SMS and push providers would have sent a Customer, for Staff (ADR 0005): {@code
 * /mock-providers/sms} and {@code /mock-providers/push}, newest first, the mobile number masked.
 */
@RestController
@RequestMapping("/mock-providers")
class MockProviderController {

  record MessageResponse(
      String reference,
      String channel,
      String customerId,
      String to,
      String title,
      String text,
      Instant sentAt) {

    static MessageResponse of(MockedMessage e) {
      return new MessageResponse(
          e.reference(),
          e.channel().name(),
          e.customerId(),
          e.channel().mask(e.to()),
          e.title(),
          e.text(),
          e.sentAt());
    }
  }

  private final MockProviderReadsUseCase mocks;

  MockProviderController(MockProviderReadsUseCase mocks) {
    this.mocks = mocks;
  }

  @GetMapping("/{channel}")
  @PreAuthorize("hasRole('STAFF')")
  Page<MessageResponse> sent(
      @PathVariable String channel,
      @RequestParam String customerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var mocked =
        switch (channel) {
          case "sms" -> NotificationChannel.SMS;
          case "push" -> NotificationChannel.PUSH;
          default -> throw new NoSuchMockProviderException(channel);
        };
    if (page < 0) {
      throw new InvalidRequestException("page", "counts from 0");
    }
    if (size < 1 || size > NotificationController.MAX_PAGE_SIZE) {
      throw new InvalidRequestException("size", "is 1 to " + NotificationController.MAX_PAGE_SIZE);
    }
    return mocks.sent(mocked, customerId, page, size).map(MessageResponse::of);
  }

  static class NoSuchMockProviderException extends RuntimeException {

    NoSuchMockProviderException(String channel) {
      super("There is no mock provider for " + channel + ": only sms and push are mocked");
    }
  }

  @ExceptionHandler(NoSuchMockProviderException.class)
  ProblemDetail notFound(NoSuchMockProviderException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalid(InvalidRequestException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("errors", List.of(e.violation()));
    return problem;
  }
}
