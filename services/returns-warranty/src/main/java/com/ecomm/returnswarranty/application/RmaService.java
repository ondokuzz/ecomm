package com.ecomm.returnswarranty.application;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.returnswarranty.application.port.in.Page;
import com.ecomm.returnswarranty.application.port.in.RmaFilter;
import com.ecomm.returnswarranty.application.port.in.RmaUseCase;
import com.ecomm.returnswarranty.application.port.out.OrderStore;
import com.ecomm.returnswarranty.application.port.out.ProductStore;
import com.ecomm.returnswarranty.application.port.out.RmaEvent;
import com.ecomm.returnswarranty.application.port.out.RmaRepository;
import com.ecomm.returnswarranty.application.port.out.ShipmentStore;
import com.ecomm.returnswarranty.application.port.out.TimeSource;
import com.ecomm.returnswarranty.application.port.out.Transactions;
import com.ecomm.returnswarranty.domain.Eligibility;
import com.ecomm.returnswarranty.domain.InspectionOutcome;
import com.ecomm.returnswarranty.domain.Product;
import com.ecomm.returnswarranty.domain.Rma;
import com.ecomm.returnswarranty.domain.RmaExceptions.NotDeliveredException;
import com.ecomm.returnswarranty.domain.RmaExceptions.OrderNotKnownYetException;
import com.ecomm.returnswarranty.domain.RmaExceptions.ProductNotKnownYetException;
import com.ecomm.returnswarranty.domain.RmaKind;
import com.ecomm.returnswarranty.domain.RmaRequest;
import com.ecomm.returnswarranty.domain.RmaStatus;
import com.ecomm.returnswarranty.domain.WarrantyTerms;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Requests RMAs against the delivered Shipments projected, one at a time per Order under the
 * Shipment's lock, and records every later step under the RMA's. Each change publishes the RMA's
 * event in its transaction.
 */
public class RmaService implements RmaUseCase {

  private final ShipmentStore shipments;
  private final ProductStore products;
  private final OrderStore orders;
  private final RmaRepository rmas;
  private final IntegrationEventPublisher events;
  private final Transactions transactions;
  private final TimeSource time;
  private final Duration returnPeriod;
  private final Duration sendBackPeriod;

  public RmaService(
      ShipmentStore shipments,
      ProductStore products,
      OrderStore orders,
      RmaRepository rmas,
      IntegrationEventPublisher events,
      Transactions transactions,
      TimeSource time,
      Duration returnPeriod,
      Duration sendBackPeriod) {
    this.shipments = shipments;
    this.products = products;
    this.orders = orders;
    this.rmas = rmas;
    this.events = events;
    this.transactions = transactions;
    this.time = time;
    this.returnPeriod = returnPeriod;
    this.sendBackPeriod = sendBackPeriod;
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
          var warranty = warrantyFor(request);
          var order =
              orders
                  .find(request.orderId())
                  .orElseThrow(() -> new OrderNotKnownYetException(request.orderId()));
          var rma =
              Rma.request(
                  UUID.randomUUID(),
                  request,
                  delivery,
                  rmas.ofOrder(request.orderId()),
                  returnPeriod,
                  warranty,
                  order,
                  time.now());
          rmas.insert(rma);
          events.publish(RmaEvent.of(rma));
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

  /**
   * The Order's Shipment is locked before the RMA, as when a Shipment event fills in Warehouses, so
   * an approval racing it can't be left without one.
   */
  @Override
  public Optional<Rma> approve(UUID id, String staffId, String note) {
    return transactions.inTransaction(
        () ->
            rmas.find(id)
                .flatMap(
                    found -> {
                      var warehouseId =
                          shipments
                              .lockDeliveryOf(found.orderId())
                              .map(d -> d.warehouseId())
                              .orElse(null);
                      return applyLocked(
                          id,
                          rma ->
                              rma.approve(staffId, note, time.now(), sendBackPeriod, warehouseId));
                    }));
  }

  @Override
  public Optional<Rma> reject(UUID id, String staffId, String reason) {
    return step(id, rma -> rma.reject(staffId, reason, time.now()));
  }

  @Override
  public Optional<Rma> receive(UUID id, String staffId, List<String> serialNumbers, int quantity) {
    return step(id, rma -> rma.receive(staffId, serialNumbers, quantity, time.now()));
  }

  @Override
  public Optional<Rma> inspect(UUID id, String staffId, InspectionOutcome outcome, String note) {
    return step(id, rma -> rma.inspect(staffId, outcome, note, time.now()));
  }

  @Override
  public Optional<Resolved> resolve(
      UUID id,
      RmaStatus resolution,
      String paymentTransactionReference,
      UUID replacementShipmentId,
      String note) {
    return transactions.inTransaction(
        () ->
            applyLocked(
                    id,
                    rma ->
                        rma.resolve(
                            resolution,
                            paymentTransactionReference,
                            replacementShipmentId,
                            note,
                            time.now()))
                .map(
                    rma ->
                        new Resolved(
                            rma,
                            shipments
                                .deliveryOf(rma.orderId())
                                .map(d -> d.fullyRefundedBy(rmas.ofOrder(rma.orderId())))
                                .orElse(false))));
  }

  @Override
  public Optional<Rma> hold(UUID id, String reason) {
    return step(id, rma -> rma.hold(reason, time.now()));
  }

  @Override
  public Optional<Rma> expire(UUID id) {
    return step(id, rma -> rma.expire(time.now()));
  }

  private Optional<Rma> step(UUID id, UnaryOperator<Rma> step) {
    return transactions.inTransaction(() -> applyLocked(id, step));
  }

  /**
   * Applies the step to the RMA under its lock, keeps it and publishes it, in the caller's
   * transaction.
   */
  private Optional<Rma> applyLocked(UUID id, UnaryOperator<Rma> step) {
    return rmas.lock(id)
        .map(
            rma -> {
              var changed = step.apply(rma);
              rmas.update(changed);
              events.publish(RmaEvent.of(changed));
              return changed;
            });
  }

  @Override
  public Optional<Rma> rma(UUID id) {
    return rmas.find(id);
  }

  @Override
  public Page<Rma> rmas(RmaFilter filter, int page, int size) {
    return rmas.find(filter, page, size);
  }

  @Override
  public List<Eligibility> eligibility(String customerId, String orderId) {
    var delivery =
        shipments
            .deliveryOf(orderId)
            .filter(d -> d.customerId().equals(customerId))
            .orElseThrow(() -> new NotDeliveredException(orderId));
    var ofOrder = rmas.ofOrder(orderId);
    return delivery.lines().stream()
        .map(
            line ->
                Eligibility.of(
                    delivery,
                    line,
                    returnPeriod,
                    products
                        .productOf(line.variantId())
                        .flatMap(Product::warrantyTerms)
                        .orElse(null),
                    ofOrder))
        .toList();
  }
}
