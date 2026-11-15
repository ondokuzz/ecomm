package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.CartEventsUseCase;
import com.ecomm.notifications.application.port.out.Carts;
import com.ecomm.notifications.application.port.out.Carts.CustomerCart;
import com.ecomm.notifications.application.port.out.ListedProducts;
import com.ecomm.notifications.application.port.out.ListedProducts.ListedVariant;
import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.application.port.out.ProductNames;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.application.port.out.Transactions;
import com.ecomm.notifications.domain.CartAbandoned;
import com.ecomm.notifications.domain.RemindedLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An Abandoned Cart records a {@code CART_ABANDONED} Cart reminder, by email alone, when its
 * Customer turned cart reminders on and Cart still holds it unchanged: not gone, not emptied, and
 * last changed no later than the event says (#105). Its lines are the Cart's as Cart answers it
 * now, named from the Catalog projection, with Price and Stock from Search & Discovery. A line no
 * longer sold is left out, and a Cart left with none records nothing. Cart and Search are read
 * before the transaction.
 */
public class CartEventsService implements CartEventsUseCase {

  private static final Logger log = LoggerFactory.getLogger(CartEventsService.class);

  private final Ledger ledger;
  private final NotificationPreferencesStore preferences;
  private final Carts carts;
  private final ProductNames products;
  private final ListedProducts listings;
  private final String cartUrl;
  private final Transactions transactions;

  public CartEventsService(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      Carts carts,
      ProductNames products,
      ListedProducts listings,
      String cartUrl,
      Transactions transactions,
      TimeSource time) {
    this.ledger = new Ledger(notifications, preferences, time);
    this.preferences = preferences;
    this.carts = carts;
    this.products = products;
    this.listings = listings;
    this.cartUrl = cartUrl;
    this.transactions = transactions;
  }

  @Override
  public List<String> cartAbandoned(CartAbandonedEvent event) {
    if (!preferences.preferencesOf(event.customerId()).cartReminders()) {
      return List.of();
    }
    var cart = carts.cartOf(event.customerId()).orElse(null);
    if (cart == null) {
      log.info("Skipped event {}: the Customer's Cart is gone or empty", event.eventId());
      return List.of();
    }
    if (cart.lastChangedAt().isAfter(event.lastChangedAt())) {
      log.info("Skipped event {}: the Cart changed since it was abandoned", event.eventId());
      return List.of();
    }
    var lines = linesOf(cart);
    if (lines.isEmpty()) {
      log.info("Skipped event {}: nothing in the Cart is sold any more", event.eventId());
      return List.of();
    }
    var content = new CartAbandoned(event.customerId(), lines, cartUrl);
    return transactions.inTransaction(() -> ledger.record(event.eventId(), content));
  }

  /** The Cart's lines still sold, in its order. */
  private List<RemindedLine> linesOf(CustomerCart cart) {
    var variantIds = cart.lines().stream().map(CustomerCart.Line::variantId).toList();
    var projected = products.variantsOf(variantIds);
    var listed = new HashMap<String, Optional<Map<String, ListedVariant>>>();
    var lines = new ArrayList<RemindedLine>();
    for (var line : cart.lines()) {
      var variant = projected.get(line.variantId());
      if (variant == null) {
        continue; // a Variant Catalog never published, so never sold
      }
      var listing =
          listed
              .computeIfAbsent(variant.sku(), listings::variantsOf)
              .map(variants -> variants.get(line.variantId()))
              .orElse(null);
      if (listing == null) {
        continue; // its Product removed, or the Variant taken off it
      }
      var priceChanged =
          variant.priceChangedAt() != null
              && variant.priceChangedAt().isAfter(cart.lastChangedAt());
      lines.add(
          new RemindedLine(
              variant.name(), line.quantity(), listing.price(), listing.inStock(), priceChanged));
    }
    return lines;
  }
}
