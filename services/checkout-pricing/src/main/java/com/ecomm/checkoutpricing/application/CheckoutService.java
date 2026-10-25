package com.ecomm.checkoutpricing.application;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUseCase;
import com.ecomm.checkoutpricing.application.port.out.AddressValidator;
import com.ecomm.checkoutpricing.application.port.out.CartPort;
import com.ecomm.checkoutpricing.application.port.out.CatalogPort;
import com.ecomm.checkoutpricing.application.port.out.CheckoutSaga;
import com.ecomm.checkoutpricing.application.port.out.CheckoutSessionRepository;
import com.ecomm.checkoutpricing.application.port.out.InventoryPort;
import com.ecomm.checkoutpricing.application.port.out.PromotionsPort;
import com.ecomm.checkoutpricing.application.port.out.ShippingQuotes;
import com.ecomm.checkoutpricing.application.port.out.TaxCalculator;
import com.ecomm.checkoutpricing.application.port.out.TimeSource;
import com.ecomm.checkoutpricing.domain.CartLine;
import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.CheckoutSessionExpiredException;
import com.ecomm.checkoutpricing.domain.CheckoutSessionNotFoundException;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.EmptyCartException;
import com.ecomm.checkoutpricing.domain.NoPaymentAttemptException;
import com.ecomm.checkoutpricing.domain.PaymentAttempt;
import com.ecomm.checkoutpricing.domain.PricedCart;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.checkoutpricing.domain.ShippingAddress;
import com.ecomm.checkoutpricing.domain.ShippingIncompleteException;
import com.ecomm.checkoutpricing.domain.UndeliverableCountryException;
import com.ecomm.checkoutpricing.domain.UnknownVariantsException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks out in two steps. Starting prices the Cart and holds its Stock through a Reservation, in a
 * Checkout Session, which then takes a Shipping address and a Shipping option. Paying hands the
 * session to the checkout Saga, which places the Order, takes the Payment and the Stock, and undoes
 * what it must when a step fails.
 */
public class CheckoutService implements CheckoutUseCase {

  private static final System.Logger log = System.getLogger(CheckoutService.class.getName());

  private final CartPort cart;
  private final CatalogPort catalog;
  private final InventoryPort inventory;
  private final CheckoutSaga saga;
  private final PromotionsPort promotions;
  private final TaxCalculator taxes;
  private final CheckoutSessionRepository sessions;
  private final AddressValidator addresses;
  private final ShippingQuotes shippingQuotes;
  private final TimeSource time;

  public CheckoutService(
      CartPort cart,
      CatalogPort catalog,
      InventoryPort inventory,
      CheckoutSaga saga,
      PromotionsPort promotions,
      TaxCalculator taxes,
      CheckoutSessionRepository sessions,
      AddressValidator addresses,
      ShippingQuotes shippingQuotes,
      TimeSource time) {
    this.cart = cart;
    this.catalog = catalog;
    this.inventory = inventory;
    this.saga = saga;
    this.promotions = promotions;
    this.taxes = taxes;
    this.sessions = sessions;
    this.addresses = addresses;
    this.shippingQuotes = shippingQuotes;
    this.time = time;
  }

  @Override
  public CheckoutSession start(String customerId) {
    var lines = cart.lines();
    if (lines.isEmpty()) {
      throw new EmptyCartException();
    }
    var priced = price(lines);
    // Before any Stock is held, so a Promotions failure holds none.
    var discounts = promotions.evaluate(priced.lines(), Optional.empty());
    var tax = taxes.tax(priced, Discount.total(discounts, priced.subtotal().currency()));

    // The old session's hold would otherwise count against the new one's.
    sessions.findByCustomer(customerId).ifPresent(this::discard);

    var now = time.now();
    var expiresAt = now.plus(CheckoutSession.LIFETIME);
    var reservationExpiresAt = CheckoutSession.reservationExpiresAt(expiresAt);
    var reservationId = inventory.reserve(customerId, lines, reservationExpiresAt);
    var session =
        new CheckoutSession(
            UUID.randomUUID().toString(),
            customerId,
            priced,
            discounts,
            tax,
            reservationId,
            expiresAt);
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
  public CheckoutSession applyCoupon(String customerId, String sessionId, String couponCode) {
    var session = live(customerId, sessionId);
    var discounted = discounted(session, Optional.of(couponCode));
    // In place of any Coupon it held, which a refusal leaves held.
    promotions.hold(
        session.id(), customerId, discounted.couponCode().orElseThrow(), session.expiresAt());
    try {
      return replaced(discounted);
    } catch (CheckoutSessionNotFoundException e) {
      promotions.release(session.id());
      throw e;
    }
  }

  @Override
  public CheckoutSession removeCoupon(String customerId, String sessionId) {
    var session = live(customerId, sessionId);
    var discounted = discounted(session, Optional.empty());
    if (session.couponCode().isPresent()) {
      promotions.release(session.id());
    }
    return replaced(discounted);
  }

  @Override
  public CheckoutSession setShippingAddress(
      String customerId, String sessionId, ShippingAddress address) {
    var session = live(customerId, sessionId);
    var validated = addresses.validate(address);
    var options =
        shippingQuotes.quote(validated, session.cart().lines(), session.subtotal().currency());
    if (options.isEmpty()) {
      throw new UndeliverableCountryException(validated.country());
    }
    return replaced(session.withShippingAddress(validated, options));
  }

  @Override
  public CheckoutSession chooseShippingOption(
      String customerId, String sessionId, String optionId) {
    var session = live(customerId, sessionId);
    return replaced(session.withShippingOption(optionId));
  }

  @Override
  public PaymentAttempt pay(String customerId, String sessionId, String paymentMethod) {
    var session = live(customerId, sessionId);
    if (!session.hasShipping()) {
      throw new ShippingIncompleteException(sessionId);
    }
    // Its session ends once it is paid, but until then, paying it again must not buy it twice.
    var paid = saga.latestAttempt(sessionId).filter(a -> a.status() == PaymentAttempt.Status.PAID);
    return paid.orElseGet(() -> saga.pay(session, paymentMethod));
  }

  @Override
  public PaymentAttempt latestPayment(String customerId, String sessionId) {
    return saga.latestAttempt(sessionId)
        .filter(attempt -> attempt.customerId().equals(customerId))
        .orElseThrow(() -> new NoPaymentAttemptException(sessionId));
  }

  @Override
  public void end(String customerId, String sessionId) {
    sessions
        .findByCustomer(customerId)
        .filter(session -> session.id().equals(sessionId))
        .ifPresent(sessions::delete);
  }

  /**
   * The Customer's Checkout Session with this ID.
   *
   * @throws CheckoutSessionNotFoundException when they have none with it
   * @throws CheckoutSessionExpiredException when it has expired
   */
  private CheckoutSession live(String customerId, String sessionId) {
    var session =
        sessions
            .find(sessionId)
            .filter(s -> s.customerId().equals(customerId))
            .orElseThrow(() -> new CheckoutSessionNotFoundException(sessionId));
    if (session.isExpiredAt(time.now())) {
      throw new CheckoutSessionExpiredException(sessionId);
    }
    return session;
  }

  /**
   * {@code session} with every Discount it is due now, with the Coupon {@code couponCode} or
   * without one, taxed again for them. The Campaigns are asked about again too, since one may have
   * started or ended since.
   */
  private CheckoutSession discounted(CheckoutSession session, Optional<String> couponCode) {
    var discounts = promotions.evaluate(session.cart().lines(), couponCode);
    var tax = taxes.tax(session.cart(), Discount.total(discounts, session.subtotal().currency()));
    return session.withDiscounts(discounts, tax);
  }

  /** Keeps {@code updated} in place of the session it changes, unless that has gone meanwhile. */
  private CheckoutSession replaced(CheckoutSession updated) {
    // Replaced or lapsed while it was being changed.
    if (!sessions.replace(updated)) {
      throw new CheckoutSessionNotFoundException(updated.id());
    }
    return updated;
  }

  /**
   * Every line at Catalog's current Price, with its Product's SKU and Category; whatever price the
   * Cart shows is ignored.
   */
  private PricedCart price(List<CartLine> lines) {
    var priced = new ArrayList<PricedLine>();
    var unknown = new ArrayList<String>();
    for (var line : lines) {
      catalog
          .variant(line.variantId())
          .ifPresentOrElse(
              variant ->
                  priced.add(
                      new PricedLine(
                          line.variantId(),
                          variant.sku(),
                          variant.category(),
                          line.quantity(),
                          variant.price())),
              () -> unknown.add(line.variantId()));
    }
    if (!unknown.isEmpty()) {
      throw new UnknownVariantsException(unknown);
    }
    return new PricedCart(priced);
  }

  /** Gives a replaced session's Stock and Coupon back and forgets it. */
  private void discard(CheckoutSession session) {
    inventory.release(session.customerId(), session.reservationId());
    if (session.couponCode().isPresent()) {
      promotions.release(session.id());
    }
    sessions.delete(session);
    log.log(
        System.Logger.Level.INFO,
        "Replaced Checkout Session "
            + session.id()
            + ", releasing Reservation "
            + session.reservationId());
  }
}
