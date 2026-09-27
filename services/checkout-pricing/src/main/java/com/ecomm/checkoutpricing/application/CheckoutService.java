package com.ecomm.checkoutpricing.application;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUseCase;
import com.ecomm.checkoutpricing.application.port.out.CartPort;
import com.ecomm.checkoutpricing.application.port.out.CatalogPort;
import com.ecomm.checkoutpricing.application.port.out.InventoryPort;
import com.ecomm.checkoutpricing.application.port.out.OrderPort;
import com.ecomm.checkoutpricing.application.port.out.PaymentPort;
import com.ecomm.checkoutpricing.application.port.out.TaxCalculator;
import com.ecomm.checkoutpricing.domain.CartLine;
import com.ecomm.checkoutpricing.domain.CheckoutResult;
import com.ecomm.checkoutpricing.domain.EmptyCartException;
import com.ecomm.checkoutpricing.domain.OrderStatus;
import com.ecomm.checkoutpricing.domain.PricedCart;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.checkoutpricing.domain.UnknownVariantsException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks out synchronously, one service after another. Until the Sagas arrive, the only
 * compensation is cancelling the Order: Stock already taken and a Payment already authorized stay
 * that way.
 */
public class CheckoutService implements CheckoutUseCase {

  private static final System.Logger log = System.getLogger(CheckoutService.class.getName());

  private final CartPort cart;
  private final CatalogPort catalog;
  private final OrderPort orders;
  private final InventoryPort inventory;
  private final PaymentPort payments;
  private final TaxCalculator taxes;

  public CheckoutService(
      CartPort cart,
      CatalogPort catalog,
      OrderPort orders,
      InventoryPort inventory,
      PaymentPort payments,
      TaxCalculator taxes) {
    this.cart = cart;
    this.catalog = catalog;
    this.orders = orders;
    this.inventory = inventory;
    this.payments = payments;
    this.taxes = taxes;
  }

  @Override
  public CheckoutResult checkout(String customerId) {
    var lines = cart.lines();
    if (lines.isEmpty()) {
      throw new EmptyCartException();
    }
    var priced = price(lines);
    var tax = taxes.tax(priced);
    if (tax.amountMinor() != 0) {
      // An Order can't record tax yet, so its total would fall short of the Payment (#14).
      throw new IllegalStateException(
          "Non-zero tax isn't supported until an Order carries its tax (#14)");
    }
    var total = priced.total(tax);

    var orderId = orders.place(customerId, priced.lines());
    try {
      inventory.decrement(lines);
      payments.authorize(customerId, orderId, total);
      orders.changeStatus(customerId, orderId, OrderStatus.PAID);
    } catch (RuntimeException e) {
      cancel(customerId, orderId, e);
      throw e;
    }

    clearCart(orderId);
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
}
