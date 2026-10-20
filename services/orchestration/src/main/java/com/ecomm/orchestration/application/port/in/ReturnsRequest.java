package com.ecomm.orchestration.application.port.in;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import java.time.Duration;
import java.time.Instant;

/**
 * What the returns Saga is started with: the approved RMA, as its {@code APPROVED} event carried
 * it; how long to await an Inspection, or a replacement's packing, before reading again; and the
 * Correlation ID of that event, which every step carries.
 *
 * <p>An RMA approved in Sprint 5 has no {@code refundAmount} until its Order is projected, and its
 * {@code APPROVED} event has no {@code sendBackBy}: the Saga takes both from the RMA it reads.
 */
public record ReturnsRequest(
    String rmaId,
    String reference,
    String orderId,
    String customerId,
    String variantId,
    int quantity,
    String kind,
    Amount refundAmount,
    Instant sendBackBy,
    Duration inspectionCheck,
    Duration packingCheck,
    String correlationId) {}
