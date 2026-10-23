package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.RmaEventsUseCase;
import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.application.port.out.ReturnAddresses;
import com.ecomm.notifications.application.port.out.ReturnAddresses.WarehousesUnavailableException;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.application.port.out.Transactions;
import com.ecomm.notifications.domain.MessageContent;
import com.ecomm.notifications.domain.ReturnAddress;
import com.ecomm.notifications.domain.RmaApproved;
import com.ecomm.notifications.domain.RmaClosed;
import com.ecomm.notifications.domain.RmaExpired;
import com.ecomm.notifications.domain.RmaReceived;
import com.ecomm.notifications.domain.RmaRejected;
import com.ecomm.notifications.domain.RmaRequested;
import com.ecomm.notifications.domain.RmaSummary;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An RMA's change to {@code REQUESTED}, {@code APPROVED}, {@code REJECTED}, {@code RECEIVED},
 * {@code CLOSED} or {@code EXPIRED} records the {@code RMA_} Message type of that name, by email
 * alone; an approval's Warehouse's Return address is read from Fulfillment & Shipping before the
 * transaction. Every other change records nothing: an Inspection, which the resolution after it
 * tells; {@code REFUNDED}, which {@code PAYMENT_REFUNDED} tells; {@code EXCHANGED}, which {@code
 * REPLACEMENT_SHIPPED} tells; a hold, which is Staff's to settle; and a backfill, which is no step.
 */
public class RmaEventsService implements RmaEventsUseCase {

  private static final Logger log = LoggerFactory.getLogger(RmaEventsService.class);

  private final Ledger ledger;
  private final ReturnAddresses returnAddresses;
  private final Transactions transactions;

  public RmaEventsService(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      ReturnAddresses returnAddresses,
      Transactions transactions,
      TimeSource time) {
    this.ledger = new Ledger(notifications, preferences, time);
    this.returnAddresses = returnAddresses;
    this.transactions = transactions;
  }

  @Override
  public List<String> rmaChanged(RmaChanged event) {
    var content = contentOf(event);
    if (content == null) {
      return List.of();
    }
    return transactions.inTransaction(() -> ledger.record(event.eventId(), content));
  }

  /**
   * @throws WarehousesUnavailableException for an approval whose Return address can't be read now,
   *     so the event is retried
   */
  private MessageContent contentOf(RmaChanged e) {
    var rma =
        new RmaSummary(
            e.orderId(), e.rmaId(), e.customerId(), e.kind(), e.productName(), e.quantity());
    return switch (e.change()) {
      case "REQUESTED" -> new RmaRequested(rma);
      case "APPROVED" -> new RmaApproved(rma, returnAddressOf(e), e.sendBackBy());
      case "REJECTED" -> new RmaRejected(rma, e.rejectionReason());
      case "RECEIVED" -> new RmaReceived(rma);
      case "CLOSED" -> new RmaClosed(rma, e.inspectionNote());
      case "EXPIRED" -> new RmaExpired(rma, e.sendBackBy());
      default -> null;
    };
  }

  /** {@code null} while the RMA's Warehouse isn't known, or isn't configured. */
  private ReturnAddress returnAddressOf(RmaChanged e) {
    if (e.warehouseId() == null) {
      return null;
    }
    var address = returnAddresses.of(e.warehouseId());
    if (address.isEmpty()) {
      log.warn(
          "RMA {} goes back to Warehouse {}, which Fulfillment & Shipping doesn't know: its"
              + " approval is sent without a Return address",
          e.rmaId(),
          e.warehouseId());
    }
    return address.orElse(null);
  }
}
