package com.ecomm.returnswarranty.domain;

import java.util.UUID;

/**
 * What resolved an RMA, as the returns Saga reports it: the Refund's Payment transaction reference
 * for a {@code REFUNDED} one, or the replacement Shipment for an {@code EXCHANGED} one. A {@code
 * CLOSED} one has neither.
 */
public record Resolution(String paymentTransactionReference, UUID replacementShipmentId) {}
