package com.ecomm.returnswarranty.application.port.in;

import com.ecomm.returnswarranty.domain.Rma;
import com.ecomm.returnswarranty.domain.RmaExceptions;
import com.ecomm.returnswarranty.domain.RmaRequest;
import java.util.Optional;
import java.util.UUID;

/** Customers request RMAs; Staff decide them; both read them. */
public interface RmaUseCase {

  /**
   * Requests an RMA against the Order's delivered Shipment.
   *
   * @throws RmaExceptions.NotDeliveredException when the Order has no delivered Shipment of the
   *     Customer's
   * @throws RmaExceptions.ProductNotKnownYetException for a warranty claim on a Variant whose
   *     Product isn't projected yet
   * @throws RuntimeException any of {@link Rma#request}'s refusals
   */
  Rma request(RmaRequest request);

  /** Staff approve it, with an optional note; empty when there is no such RMA. */
  Optional<Rma> approve(UUID id, String staffId, String note);

  /** Staff reject it, with the reason; empty when there is no such RMA. */
  Optional<Rma> reject(UUID id, String staffId, String reason);

  Optional<Rma> rma(UUID id);

  /** A page of the RMAs that match, oldest first. */
  Page<Rma> rmas(RmaFilter filter, int page, int size);
}
