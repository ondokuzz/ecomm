package com.ecomm.fulfillmentshipping.adapter.in.web;

import com.ecomm.fulfillmentshipping.application.port.in.RecordTrackingEventUseCase;
import com.ecomm.fulfillmentshipping.domain.TrackingEvent;
import com.ecomm.fulfillmentshipping.domain.TrackingStatus;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Carrier webhooks, each reporting a Tracking event of a booked Shipment: {@code {"eventId",
 * "trackingNumber", "status", "occurredAt"}}. The Carrier calls from outside with no token, so a
 * webhook is authenticated by its signature instead (see {@link CarrierWebhookSignature}).
 */
@RestController
@EnableConfigurationProperties(CarrierWebhookController.CarrierWebhookSecret.class)
class CarrierWebhookController {

  private static final Logger log = LoggerFactory.getLogger(CarrierWebhookController.class);

  /** {@code secret}: what Carrier webhooks are signed with, shared with the Carrier. */
  @ConfigurationProperties("ecomm.fulfillment-shipping.carrier-webhooks")
  record CarrierWebhookSecret(String secret) {}

  record CarrierWebhookRequest(
      String eventId, String trackingNumber, String status, String occurredAt) {

    TrackingEvent toTrackingEvent() {
      if (eventId == null || eventId.isBlank() || eventId.length() > 255) {
        throw new InvalidRequestException("eventId", "must be 1 to 255 characters");
      }
      if (trackingNumber == null || trackingNumber.isBlank()) {
        throw new InvalidRequestException("trackingNumber", "must be a non-blank string");
      }
      TrackingStatus trackingStatus;
      try {
        trackingStatus = TrackingStatus.valueOf(status);
      } catch (IllegalArgumentException | NullPointerException e) {
        throw new InvalidRequestException(
            "status", "must be IN_TRANSIT, OUT_FOR_DELIVERY or DELIVERED");
      }
      try {
        return new TrackingEvent(eventId, trackingStatus, Instant.parse(occurredAt));
      } catch (DateTimeParseException | NullPointerException e) {
        throw new InvalidRequestException("occurredAt", "must be an ISO 8601 instant");
      }
    }
  }

  private final RecordTrackingEventUseCase tracking;
  private final CarrierWebhookSignature signature;
  private final JsonMapper json;

  CarrierWebhookController(
      RecordTrackingEventUseCase tracking, CarrierWebhookSecret secret, JsonMapper json) {
    this.tracking = tracking;
    this.signature = new CarrierWebhookSignature(secret.secret());
    this.json = json;
  }

  /**
   * 200 once the Tracking event is recorded, and for a repeat of one already recorded, which
   * changes nothing; 401 when its signature is missing or wrong; 400 when it is malformed; 404 when
   * no Shipment has its tracking number, so the Carrier sends it again later.
   */
  @PostMapping("/webhooks/carrier")
  ResponseEntity<Void> receive(
      @RequestHeader(name = CarrierWebhookSignature.HEADER, required = false) String signed,
      @RequestBody byte[] body) {
    if (!signature.signs(signed, body)) {
      throw new UnsignedWebhookException();
    }
    var request = read(body);
    var event = request.toTrackingEvent();
    var recorded =
        tracking
            .record(request.trackingNumber(), event)
            .orElseThrow(() -> new UnknownTrackingNumberException(request.trackingNumber()));
    var shipment = recorded.shipment();
    if (recorded.isNew()) {
      log.info(
          "Carrier event {} ({}) recorded for Shipment {}, which is {}",
          event.eventId(),
          event.status(),
          shipment.id(),
          shipment.status());
    } else {
      log.info("Carrier event {} was received before; nothing changed", event.eventId());
    }
    return ResponseEntity.ok().build();
  }

  private CarrierWebhookRequest read(byte[] body) {
    try {
      var request = json.readValue(body, CarrierWebhookRequest.class);
      if (request == null) {
        throw new InvalidRequestException("body", "is required");
      }
      return request;
    } catch (JacksonException e) {
      throw new InvalidRequestException("body", "must be a JSON object");
    }
  }

  static class UnsignedWebhookException extends RuntimeException {}

  static class UnknownTrackingNumberException extends RuntimeException {

    UnknownTrackingNumberException(String trackingNumber) {
      super("No Shipment has the tracking number " + trackingNumber);
    }
  }

  @ExceptionHandler(UnsignedWebhookException.class)
  ProblemDetail unsigned() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.UNAUTHORIZED, "The webhook's signature is missing or wrong.");
  }

  @ExceptionHandler(UnknownTrackingNumberException.class)
  ProblemDetail unknown(UnknownTrackingNumberException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalid(InvalidRequestException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
