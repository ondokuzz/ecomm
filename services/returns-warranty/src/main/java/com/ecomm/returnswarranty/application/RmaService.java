package com.ecomm.returnswarranty.application;

import com.ecomm.returnswarranty.application.port.in.Page;
import com.ecomm.returnswarranty.application.port.in.RmaFilter;
import com.ecomm.returnswarranty.application.port.in.RmaUseCase;
import com.ecomm.returnswarranty.application.port.out.ProductStore;
import com.ecomm.returnswarranty.application.port.out.RmaRepository;
import com.ecomm.returnswarranty.application.port.out.ShipmentStore;
import com.ecomm.returnswarranty.application.port.out.TimeSource;
import com.ecomm.returnswarranty.application.port.out.Transactions;
import com.ecomm.returnswarranty.domain.Rma;
import com.ecomm.returnswarranty.domain.RmaExceptions.NotDeliveredException;
import com.ecomm.returnswarranty.domain.RmaExceptions.ProductNotKnownYetException;
import com.ecomm.returnswarranty.domain.RmaKind;
import com.ecomm.returnswarranty.domain.RmaRequest;
import com.ecomm.returnswarranty.domain.WarrantyTerms;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Requests RMAs against the delivered Shipments projected, one at a time per Order under the
 * Shipment's lock, and records Staff's decisions under the RMA's.
 */
public class RmaService implements RmaUseCase {

  private final ShipmentStore shipments;
  private final ProductStore products;
  private final RmaRepository rmas;
  private final Transactions transactions;
  private final TimeSource time;
  private final Duration returnPeriod;

  public RmaService(
      ShipmentStore shipments,
      ProductStore products,
      RmaRepository rmas,
      Transactions transactions,
      TimeSource time,
      Duration returnPeriod) {
    this.shipments = shipments;
    this.products = products;
    this.rmas = rmas;
    this.transactions = transactions;
    this.time = time;
    this.returnPeriod = returnPeriod;
  }

  @Override
  public Rma request(RmaRequest request) {
    return transactions.inTransaction(
        () -> {
          var delivery =
              shipments
                  .lockDeliveryOf(request.orderId())
                  .filter(d -> d.customerId().equals(request.customerId()))
                  .orElseThrow(() -> new NotDeliveredException(request.orderId()));
          var rma =
              Rma.request(
                  UUID.randomUUID(),
                  request,
                  delivery,
                  rmas.ofOrder(request.orderId()),
                  returnPeriod,
                  warrantyFor(request),
                  time.now());
          rmas.insert(rma);
          return rma;
        });
  }

  /** A warranty claim's Warranty terms; a return needs none. */
  private Optional<WarrantyTerms> warrantyFor(RmaRequest request) {
    if (request.kind() != RmaKind.WARRANTY_CLAIM) {
      return Optional.empty();
    }
    return products
        .productOf(request.variantId())
        .orElseThrow(() -> new ProductNotKnownYetException(request.variantId()))
        .warrantyTerms();
  }

  @Override
  public Optional<Rma> approve(UUID id, String staffId, String note) {
    return decide(id, rma -> rma.approve(staffId, note, time.now()));
  }

  @Override
  public Optional<Rma> reject(UUID id, String staffId, String reason) {
    return decide(id, rma -> rma.reject(staffId, reason, time.now()));
  }

  private Optional<Rma> decide(UUID id, UnaryOperator<Rma> decision) {
    return transactions.inTransaction(
        () ->
            rmas.lock(id)
                .map(
                    rma -> {
                      var decided = decision.apply(rma);
                      rmas.update(decided);
                      return decided;
                    }));
  }

  @Override
  public Optional<Rma> rma(UUID id) {
    return rmas.find(id);
  }

  @Override
  public Page<Rma> rmas(RmaFilter filter, int page, int size) {
    return rmas.find(filter, page, size);
  }
}
