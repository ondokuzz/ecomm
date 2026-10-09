package com.ecomm.payment.adapter.in.web;

import com.ecomm.payment.application.port.in.SettleAuthorizationUseCase;
import com.ecomm.payment.domain.InvalidPaymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * The gateway's webhooks, settling pending authorizations. The gateway calls from outside with no
 * token, so a webhook is authenticated by its signature instead (see {@link WebhookSignature}).
 */
@RestController
class GatewayWebhookController {

  private static final Logger log = LoggerFactory.getLogger(GatewayWebhookController.class);

  private final SettleAuthorizationUseCase settle;
  private final WebhookSignature signature;
  private final JsonMapper json;

  GatewayWebhookController(
      SettleAuthorizationUseCase settle, WebhookProperties properties, JsonMapper json) {
    this.settle = settle;
    this.signature = new WebhookSignature(properties.secret());
    this.json = json;
  }

  /**
   * 200 once the webhook is recorded, and for a repeat of one already recorded, which changes
   * nothing; 401 when its signature is missing or wrong; 400 when it is malformed; 404 when no
   * Payment's authorization has its reference, so the gateway sends it again later.
   */
  @PostMapping("/webhooks/gateway")
  ResponseEntity<Void> receive(
      @RequestHeader(name = WebhookSignature.HEADER, required = false) String signed,
      @RequestBody byte[] body) {
    if (!signature.signs(signed, body)) {
      throw new UnsignedWebhookException();
    }
    var settlement = read(body).toSettlement();
    var received =
        settle
            .settle(settlement)
            .orElseThrow(() -> new UnknownReferenceException(settlement.reference()));
    var payment = received.payment();
    switch (received.outcome()) {
      case SETTLED ->
          log.info(
              "Gateway event {} settled Payment {} for Order {} as {}",
              settlement.eventId(),
              payment.id(),
              payment.orderId(),
              payment.status());
      case ALREADY_RECEIVED ->
          log.info("Gateway event {} was received before; nothing changed", settlement.eventId());
      case NOT_PENDING ->
          log.warn(
              "Gateway event {} would settle Payment {}, which is already {}; recorded, nothing"
                  + " changed",
              settlement.eventId(),
              payment.id(),
              payment.status());
    }
    return ResponseEntity.ok().build();
  }

  private GatewayWebhookRequest read(byte[] body) {
    try {
      var request = json.readValue(body, GatewayWebhookRequest.class);
      if (request == null) {
        throw new InvalidPaymentException("a webhook needs a body");
      }
      return request;
    } catch (JacksonException e) {
      throw new InvalidPaymentException("a webhook's body must be a JSON object");
    }
  }

  static class UnsignedWebhookException extends RuntimeException {}

  static class UnknownReferenceException extends RuntimeException {

    UnknownReferenceException(String reference) {
      super("No Payment's authorization has the gateway reference " + reference);
    }
  }

  @ExceptionHandler(UnsignedWebhookException.class)
  ProblemDetail unsigned() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.UNAUTHORIZED, "The webhook's signature is missing or wrong.");
  }

  @ExceptionHandler(UnknownReferenceException.class)
  ProblemDetail unknownReference(UnknownReferenceException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(InvalidPaymentException.class)
  ProblemDetail invalid(InvalidPaymentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
