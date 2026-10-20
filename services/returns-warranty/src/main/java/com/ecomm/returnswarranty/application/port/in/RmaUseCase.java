package com.ecomm.returnswarranty.application.port.in;

import com.ecomm.returnswarranty.domain.Eligibility;
import com.ecomm.returnswarranty.domain.InspectionOutcome;
import com.ecomm.returnswarranty.domain.Rma;
import com.ecomm.returnswarranty.domain.RmaExceptions;
import com.ecomm.returnswarranty.domain.RmaRequest;
import com.ecomm.returnswarranty.domain.RmaStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Customers request RMAs; Staff decide, receive and inspect them; the returns Saga resolves, holds
 * or expires them; Customers and Staff read them. Each step but a read is empty when there is no
 * such RMA, and throws {@link RmaExceptions.TransitionRefusedException} when its status doesn't
 * allow it.
 */
public interface RmaUseCase {

  /**
   * Requests an RMA against the Order's delivered Shipment.
   *
   * @throws RmaExceptions.NotDeliveredException when the Order has no delivered Shipment of the
   *     Customer's
   * @throws RmaExceptions.ProductNotKnownYetException for a warranty claim on a Variant whose
   *     Product isn't projected yet
   * @throws RmaExceptions.OrderNotKnownYetException when the Order isn't projected yet
   * @throws RuntimeException any of {@link Rma#request}'s refusals
   */
  Rma request(RmaRequest request);

  /** Staff approve it, with an optional note, setting its Send-back deadline and Warehouse. */
  Optional<Rma> approve(UUID id, String staffId, String note);

  /** Staff reject it, with the reason. */
  Optional<Rma> reject(UUID id, String staffId, String reason);

  /** Staff receive its units; see {@link Rma#receive}. */
  Optional<Rma> receive(UUID id, String staffId, List<String> serialNumbers, int quantity);

  /** Staff record its Inspection; see {@link Rma#inspect}. */
  Optional<Rma> inspect(UUID id, String staffId, InspectionOutcome outcome, String note);

  /** An RMA the returns Saga resolved, and whether every unit of its Order is now refunded. */
  record Resolved(Rma rma, boolean orderFullyRefunded) {}

  /** The returns Saga resolves it; see {@link Rma#resolve}. */
  Optional<Resolved> resolve(
      UUID id,
      RmaStatus resolution,
      String paymentTransactionReference,
      UUID replacementShipmentId,
      String note);

  /** The returns Saga holds it, with the reason. */
  Optional<Rma> hold(UUID id, String reason);

  /** The returns Saga expires it, once its Send-back deadline passed. */
  Optional<Rma> expire(UUID id);

  Optional<Rma> rma(UUID id);

  /** A page of the RMAs that match: approved ones by Send-back deadline, others oldest first. */
  Page<Rma> rmas(RmaFilter filter, int page, int size);

  /**
   * What each line the Customer's Order delivered is still eligible for.
   *
   * @throws RmaExceptions.NotDeliveredException when the Order has no delivered Shipment of the
   *     Customer's
   */
  List<Eligibility> eligibility(String customerId, String orderId);
}
