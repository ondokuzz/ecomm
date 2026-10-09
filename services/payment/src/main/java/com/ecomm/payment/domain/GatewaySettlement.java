package com.ecomm.payment.domain;

/**
 * A Gateway webhook settling a pending authorization: the gateway's ID for the event, and its final
 * answer, approved or declined, under the authorization's own reference. Throws {@link
 * InvalidPaymentException} unless it is well formed.
 */
public record GatewaySettlement(String eventId, GatewayAuthorization answer) {

  /** The longest gateway event ID a Payment transaction can hold. */
  public static final int MAX_EVENT_ID_LENGTH = 255;

  /** The longest gateway reference a Payment transaction can hold. */
  public static final int MAX_REFERENCE_LENGTH = 128;

  /** The longest decline reason a Payment transaction can hold. */
  public static final int MAX_DECLINE_REASON_LENGTH = 64;

  public GatewaySettlement {
    if (eventId == null || eventId.isBlank()) {
      throw new InvalidPaymentException("a webhook needs an eventId");
    }
    if (eventId.length() > MAX_EVENT_ID_LENGTH) {
      throw new InvalidPaymentException(
          "an eventId is at most " + MAX_EVENT_ID_LENGTH + " characters");
    }
    if (answer == null || answer instanceof GatewayAuthorization.Pending) {
      throw new InvalidPaymentException("a webhook settles as APPROVED or DECLINED");
    }
    if (answer.reference() == null || answer.reference().isBlank()) {
      throw new InvalidPaymentException("a webhook needs a reference");
    }
    if (answer.reference().length() > MAX_REFERENCE_LENGTH) {
      throw new InvalidPaymentException(
          "a reference is at most " + MAX_REFERENCE_LENGTH + " characters");
    }
    if (answer instanceof GatewayAuthorization.Declined declined
        && (declined.reason() == null
            || declined.reason().isBlank()
            || declined.reason().length() > MAX_DECLINE_REASON_LENGTH)) {
      throw new InvalidPaymentException(
          "a decline needs a declineReason of at most "
              + MAX_DECLINE_REASON_LENGTH
              + " characters");
    }
  }

  /** The gateway's reference for the authorization it settles. */
  public String reference() {
    return answer.reference();
  }
}
