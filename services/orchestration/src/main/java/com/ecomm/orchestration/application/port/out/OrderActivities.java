package com.ecomm.orchestration.application.port.out;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Discount;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Line;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingAddress;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingOption;
import io.temporal.activity.ActivityInterface;
import java.util.List;

/** Order Management's commands, each under an {@code Idempotency-Key} from the run. */
@ActivityInterface
public interface OrderActivities {

  /** An Order Order Management placed, and the total it gave it. */
  record PlacedOrder(String orderId, Amount total) {}

  /**
   * Places the Order. {@code shippingAddress} and {@code shippingOption} are null on the path of a
   * run started before Shipping, which Order Management refuses.
   */
  PlacedOrder placeOrder(
      String customerId,
      List<Line> lines,
      List<Discount> discounts,
      Amount tax,
      ShippingAddress shippingAddress,
      ShippingOption shippingOption);

  void markOrderPaid(String customerId, String orderId);

  void cancelOrder(String customerId, String orderId);
}
