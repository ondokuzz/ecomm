package com.ecomm.notifications.adapter.in.web;

import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.notifications.application.port.in.PreferencesUseCase;
import com.ecomm.notifications.domain.NotificationPreferences;
import com.ecomm.notifications.domain.NotificationPreferences.InvalidPhoneNumberException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling Customer's Notification preferences: email always on, SMS with a mobile number in
 * E.164 form, and push.
 */
@RestController
@RequestMapping("/preferences")
class PreferencesController {

  /** As a Customer reads theirs; {@code updatedAt} is {@code null} until they first save them. */
  record PreferencesResponse(
      boolean email, boolean sms, String phoneNumber, boolean push, Instant updatedAt) {

    static PreferencesResponse of(NotificationPreferences p) {
      return new PreferencesResponse(true, p.sms(), p.phoneNumber(), p.push(), p.updatedAt());
    }
  }

  /** What a Customer saves: email can't be turned off, so it isn't asked. */
  record PreferencesBody(String phoneNumber, Boolean sms, Boolean push) {}

  private final PreferencesUseCase preferences;

  PreferencesController(PreferencesUseCase preferences) {
    this.preferences = preferences;
  }

  @GetMapping("/me")
  @PreAuthorize("hasRole('CUSTOMER')")
  PreferencesResponse mine(CurrentCustomer customer) {
    return PreferencesResponse.of(preferences.preferencesOf(customer.id()));
  }

  /**
   * Replaces them; 422 {@code invalidPhoneNumber} for a number that isn't E.164, or SMS without
   * one.
   */
  @PutMapping("/me")
  @PreAuthorize("hasRole('CUSTOMER')")
  PreferencesResponse save(@RequestBody PreferencesBody body, CurrentCustomer customer) {
    if (body.sms() == null) {
      throw new InvalidRequestException("sms", "is required");
    }
    if (body.push() == null) {
      throw new InvalidRequestException("push", "is required");
    }
    return PreferencesResponse.of(
        preferences.save(customer.id(), body.phoneNumber(), body.sms(), body.push()));
  }

  @ExceptionHandler(InvalidPhoneNumberException.class)
  ProblemDetail invalidPhoneNumber(InvalidPhoneNumberException e) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    problem.setProperty("reason", "invalidPhoneNumber");
    return problem;
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalid(InvalidRequestException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("errors", List.of(e.violation()));
    return problem;
  }
}
