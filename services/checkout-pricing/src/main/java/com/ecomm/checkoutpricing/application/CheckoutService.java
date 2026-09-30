package com.ecomm.checkoutpricing.application;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUseCase;
import com.ecomm.checkoutpricing.application.port.out.CartPort;
import com.ecomm.checkoutpricing.application.port.out.CatalogPort;
import com.ecomm.checkoutpricing.application.port.out.CheckoutSessionRepository;
import com.ecomm.checkoutpricing.application.port.out.InventoryPort;
import com.ecomm.checkoutpricing.application.port.out.OrderPort;
import com.ecomm.checkoutpricing.application.port.out.PaymentPort;
import com.ecomm.checkoutpricing.application.port.out.TaxCalculator;
import com.ecomm.checkoutpricing.application.port.out.TimeSource;
import com.ecomm.checkoutpricing.domain.CartLine;
import com.ecomm.checkoutpricing.domain.CheckoutResult;
import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.CheckoutSessionExpiredException;
import com.ecomm.checkoutpricing.domain.CheckoutSessionNotFoundException;
import com.ecomm.checkoutpricing.domain.EmptyCartException;
import com.ecomm.checkoutpricing.domain.OrderStatus;
import com.ecomm.checkoutpricing.domain.PricedCart;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.checkoutpricing.domain.UnknownVariantsException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks out in two steps, synchronously, one service after another. Starting holds the Cart's
 * Stock through a Reservation; paying commits it. Until the Sagas arrive, the only compensation
 * once an Order exists is cancelling it.
 */
public class CheckoutService implements CheckoutUseCase {

  private static final System.Logger log = System.getLogger(CheckoutService.class.getName());

  private final CartPort cart;
  private final CatalogPort catalog;
  private final OrderPort orders;
  private final InventoryPort inventory;
  private final PaymentPort payments;
  private final TaxCalculator taxes;
  private final CheckoutSessionRepository sessions;
  private final TimeSource time;

  public CheckoutService(
      CartPort cart,
      CatalogPort catalog,
      OrderPort orders,
      InventoryPort inventory,
      PaymentPort payments,
      TaxCalculator taxes,
      CheckoutSessionRepository sessions,
      TimeSource time) {
    this.cart = cart;
    this.catalog = catalog;
    this.orders = orders;
    this.inventory = inventory;
    this.payments = payments;
    this.taxes = taxes;
    this.sessions = sessions;
    this.time = time;
  }

  @Override
  public CheckoutSession start(String customerId) {
    var lines = cart.lines();
    if (lines.isEmpty()) {
      throw new EmptyCartException();
    }
    var priced = price(lines);
    var tax = taxes.tax(priced);

    // The old session's hold would otherwise count against the new one's.
    sessions.findByCustomer(customerId).ifPresent(this::discard);

    var now = time.now();
    var expiresAt = now.plus(CheckoutSession.LIFETIME);
    var reservationExpiresAt = CheckoutSession.reservationExpiresAt(expiresAt);
    var reservationId = inventory.reserve(customerId, lines, reservationExpiresAt);
    var session =
        new CheckoutSession(
            UUID.randomUUID().toString(), customerId, priced, tax, reservationId, expiresAt);
    // Kept past its expiry, until its Reservation's, so paying it late is told apart from paying
    // one that never existed.
    sessions.save(session, Duration.between(now, reservationExpiresAt));
    return session;
  }

  @Override
  public Optional<CheckoutSession> current(String customerId) {
    return sessions.findByCustomer(customerId).filter(s -> !s.isExpiredAt(time.now()));
  }

  @Override
  public CheckoutResult pay(String customerId, String sessionId) {
    var session =
        sessions
            .find(sessionId)
            .filter(s -> s.customerId().equals(customerId))
            .orElseThrow(() -> new CheckoutSessionNotFoundException(sessionId));
    if (session.isExpiredAt(time.now())) {
      throw new CheckoutSessionExpiredException(sessionId);
    }

    // The Payment is for the Order's total, as Order Management records it, so the two always
    // match.
    var order = orders.place(customerId, session.cart().lines(), session.tax());
    var orderId = order.id();
    try {
      payments.authorize(customerId, orderId, order.total());
      inventory.commit(customerId, session.reservationId());
      orders.changeStatus(customerId, orderId, OrderStatus.PAID);
    } catch (RuntimeException e) {
      cancel(customerId, orderId, e);
      throw e;
    }

    clearCart(orderId);
    endSession(session, orderId);
    return new CheckoutResult(orderId, OrderStatus.PAID);
  }

  /** Every line at Catalog's current Price; whatever price the Cart shows is ignored. */
  private PricedCart price(List<CartLine> lines) {
    var priced = new ArrayList<PricedLine>();
    var unknown = new ArrayList<String>();
    for (var line : lines) {
      catalog
          .price(line.variantId())
          .ifPresentOrElse(
              price -> priced.add(new PricedLine(line.variantId(), line.quantity(), price)),
              () -> unknown.add(line.variantId()));
    }
    if (!unknown.isEmpty()) {
      throw new UnknownVariantsException(unknown);
    }
    return new PricedCart(priced);
  }

  /** Cancels the Order a failed step leaves behind; a failure to cancel rides on {@code cause}. */
  private void cancel(String customerId, String orderId, RuntimeException cause) {
    try {
      orders.changeStatus(customerId, orderId, OrderStatus.CANCELLED);
    } catch (RuntimeException e) {
      log.log(System.Logger.Level.ERROR, "Order " + orderId + " could not be cancelled", e);
      cause.addSuppressed(e);
    }
  }

  /**
   * The Order is paid by now, so a Cart that can't be cleared doesn't undo it: the Customer keeps a
   * stale Cart rather than losing a paid Order.
   */
  private void clearCart(String orderId) {
    try {
      cart.clear();
    } catch (RuntimeException e) {
      log.log(
          System.Logger.Level.WARNING, "Cart not cleared after Order " + orderId + " was paid", e);
    }
  }

  /** Likewise a session that outlives its paid Order only until it expires. */
  private void endSession(CheckoutSession session, String orderId) {
    try {
      sessions.delete(session);
    } catch (RuntimeException e) {
      log.log(
          System.Logger.Level.WARNING,
          "Checkout Session " + session.id() + " not ended after Order " + orderId + " was paid",
          e);
    }
  }

  /** Gives a replaced session's Stock back and forgets it. */
  private void discard(CheckoutSession session) {
    inventory.release(session.customerId(), session.reservationId());
    sessions.delete(session);
    log.log(
        System.Logger.Level.INFO,
        "Replaced Checkout Session "
            + session.id()
            + ", releasing Reservation "
            + session.reservationId());
  }
}
