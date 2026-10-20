package com.ecomm.fulfillmentshipping.adapter.out.postgres;

import com.ecomm.commons.money.Money;
import com.ecomm.fulfillmentshipping.application.port.in.Page;
import com.ecomm.fulfillmentshipping.application.port.out.ShipmentRepository;
import com.ecomm.fulfillmentshipping.domain.SerialTracking;
import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.SerialNumberAlreadyShippedException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ShipmentExistsException;
import com.ecomm.fulfillmentshipping.domain.ShipmentKind;
import com.ecomm.fulfillmentshipping.domain.ShipmentLine;
import com.ecomm.fulfillmentshipping.domain.ShipmentStatus;
import com.ecomm.fulfillmentshipping.domain.ShippingAddress;
import com.ecomm.fulfillmentshipping.domain.ShippingOption;
import com.ecomm.fulfillmentshipping.domain.StatusChange;
import com.ecomm.fulfillmentshipping.domain.TrackingEvent;
import com.ecomm.fulfillmentshipping.domain.TrackingStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Shipments as rows of {@code shipment}, with their lines, the Serial numbers they were packed
 * with, their status history and their Tracking events in tables of their own (see {@code
 * db/migration}). History entries and Tracking events are only ever inserted.
 */
@Component
class PostgresShipmentRepository implements ShipmentRepository {

  private static final String SHIPMENT = "SELECT * FROM shipment s ";

  private final JdbcClient jdbc;

  PostgresShipmentRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void add(Shipment shipment) {
    var address = shipment.shippingAddress();
    var option = shipment.shippingOption();
    try {
      jdbc.sql(
              """
              INSERT INTO shipment (id, kind, rma_id, order_id, customer_id, warehouse_id, status,
                version, created_at, recipient_name, line1, line2, postcode, city, region, country,
                phone, option_id, option_carrier, cost_minor, currency, estimated_days)
              VALUES (:id, :kind, :rmaId, :orderId, :customerId, :warehouseId, :status, :version,
                :createdAt,
                :recipientName, :line1, :line2, :postcode, :city, :region, :country, :phone,
                :optionId, :optionCarrier, :costMinor, :currency, :estimatedDays)
              """)
          .param("id", shipment.id())
          .param("kind", shipment.kind().name())
          .param("rmaId", shipment.rmaId())
          .param("orderId", shipment.orderId())
          .param("customerId", shipment.customerId())
          .param("warehouseId", shipment.warehouseId())
          .param("status", shipment.status().name())
          .param("version", shipment.version())
          .param("createdAt", Timestamp.from(shipment.createdAt()))
          .param("recipientName", address.recipientName())
          .param("line1", address.line1())
          .param("line2", address.line2())
          .param("postcode", address.postcode())
          .param("city", address.city())
          .param("region", address.region())
          .param("country", address.country())
          .param("phone", address.phone())
          .param("optionId", option.id())
          .param("optionCarrier", option.carrier())
          .param("costMinor", option.cost().amountMinor())
          .param("currency", option.cost().currency().getCurrencyCode())
          .param("estimatedDays", option.estimatedDays())
          .update();
    } catch (DuplicateKeyException e) {
      throw new ShipmentExistsException(shipment.kind(), shipment.orderId(), shipment.rmaId());
    }
    var lines = shipment.lines();
    for (var position = 0; position < lines.size(); position++) {
      var line = lines.get(position);
      jdbc.sql(
              """
              INSERT INTO shipment_line
                (shipment_id, position, variant_id, product_name, quantity, serial_tracking)
              VALUES (:shipmentId, :position, :variantId, :productName, :quantity, :serialTracking)
              """)
          .param("shipmentId", shipment.id())
          .param("position", position)
          .param("variantId", line.variantId())
          .param("productName", line.productName())
          .param("quantity", line.quantity())
          .param("serialTracking", line.serialTracking().name())
          .update();
    }
    addHistory(shipment);
  }

  @Override
  public void update(Shipment changed) {
    jdbc.sql(
            """
            UPDATE shipment SET status = :status, version = :version, carrier = :carrier,
              tracking_number = :trackingNumber, delivered_at = :deliveredAt,
              hold_reason = :holdReason
            WHERE id = :id
            """)
        .param("status", changed.status().name())
        .param("version", changed.version())
        .param("carrier", changed.carrier())
        .param("trackingNumber", changed.trackingNumber())
        .param(
            "deliveredAt",
            changed.deliveredAt() == null ? null : Timestamp.from(changed.deliveredAt()))
        .param("holdReason", changed.holdReason())
        .param("id", changed.id())
        .update();
    addHistory(changed);
    addSerialNumbers(changed);
  }

  /** Inserts the entries not yet kept: the history only ever grows. */
  private void addHistory(Shipment shipment) {
    var history = shipment.statusHistory();
    for (var position = 0; position < history.size(); position++) {
      var entry = history.get(position);
      jdbc.sql(
              """
              INSERT INTO shipment_status_history (shipment_id, position, status, at, changed_by)
              VALUES (:shipmentId, :position, :status, :at, :by)
              ON CONFLICT (shipment_id, position) DO NOTHING
              """)
          .param("shipmentId", shipment.id())
          .param("position", position)
          .param("status", entry.status().name())
          .param("at", Timestamp.from(entry.at()))
          .param("by", entry.by())
          .update();
    }
  }

  /**
   * Inserts the Serial numbers not yet kept, each for its line's Product. One another Shipment of
   * the Product holds, committed or not, is refused once that Shipment's transaction ends. A
   * Shipment put on hold releases its own, for another Shipment to pack.
   */
  private void addSerialNumbers(Shipment shipment) {
    var lines = shipment.lines();
    for (var position = 0; position < lines.size(); position++) {
      var line = lines.get(position);
      var serials = line.serialNumbers();
      for (var unit = 0; unit < serials.size(); unit++) {
        var serial = serials.get(unit);
        try {
          jdbc.sql(
                  """
                  INSERT INTO shipment_serial_number
                    (serial_number, shipment_id, line_position, unit, product_sku)
                  VALUES (:serial, :shipmentId, :position, :unit,
                    COALESCE((SELECT sku FROM product_variant WHERE variant_id = :variantId),
                             :variantId))
                  ON CONFLICT (shipment_id, line_position, unit) DO NOTHING
                  """)
              .param("serial", serial)
              .param("shipmentId", shipment.id())
              .param("position", position)
              .param("unit", unit)
              .param("variantId", line.variantId())
              .update();
        } catch (DuplicateKeyException e) {
          throw new SerialNumberAlreadyShippedException(serial);
        }
      }
    }
    if (shipment.status() == ShipmentStatus.ON_HOLD) {
      jdbc.sql("UPDATE shipment_serial_number SET released = true WHERE shipment_id = :id")
          .param("id", shipment.id())
          .update();
    }
  }

  @Override
  public boolean addTrackingEvent(UUID shipmentId, TrackingEvent event, Instant receivedAt) {
    return jdbc.sql(
                """
                INSERT INTO tracking_event (event_id, shipment_id, status, occurred_at, received_at)
                VALUES (:eventId, :shipmentId, :status, :occurredAt, :receivedAt)
                ON CONFLICT (event_id) DO NOTHING
                """)
            .param("eventId", event.eventId())
            .param("shipmentId", shipmentId)
            .param("status", event.status().name())
            .param("occurredAt", Timestamp.from(event.occurredAt()))
            .param("receivedAt", Timestamp.from(receivedAt))
            .update()
        == 1;
  }

  @Override
  public Optional<Shipment> find(UUID id) {
    return one(jdbc.sql(SHIPMENT + "WHERE s.id = :id").param("id", id));
  }

  @Override
  public Optional<Shipment> findByOrder(String orderId) {
    return one(
        jdbc.sql(SHIPMENT + "WHERE s.order_id = :orderId AND s.kind = 'ORDER'")
            .param("orderId", orderId));
  }

  @Override
  public Optional<Shipment> findByRma(String rmaId) {
    return one(jdbc.sql(SHIPMENT + "WHERE s.rma_id = :rmaId").param("rmaId", rmaId));
  }

  @Override
  public Optional<Shipment> lockToChange(UUID id) {
    return one(jdbc.sql(SHIPMENT + "WHERE s.id = :id FOR UPDATE").param("id", id));
  }

  @Override
  public Optional<Shipment> lockToChangeByTrackingNumber(String trackingNumber) {
    return one(
        jdbc.sql(SHIPMENT + "WHERE s.tracking_number = :trackingNumber FOR UPDATE")
            .param("trackingNumber", trackingNumber));
  }

  @Override
  public List<String> shippedElsewhere(Shipment shipment) {
    var found = new ArrayList<String>();
    for (var line : shipment.lines()) {
      if (line.serialNumbers().isEmpty()) {
        continue;
      }
      found.addAll(
          jdbc.sql(
                  """
                  SELECT serial_number FROM shipment_serial_number
                  WHERE serial_number IN (:serials) AND shipment_id <> :shipmentId
                    AND NOT released
                    AND product_sku = COALESCE(
                      (SELECT sku FROM product_variant WHERE variant_id = :variantId), :variantId)
                  ORDER BY serial_number
                  """)
              .param("serials", line.serialNumbers())
              .param("shipmentId", shipment.id())
              .param("variantId", line.variantId())
              .query(String.class)
              .list());
    }
    return found;
  }

  @Override
  public Page<Shipment> page(
      ShipmentStatus status, String warehouseId, ShipmentKind kind, int page, int size) {
    var where =
        "WHERE (CAST(:status AS VARCHAR) IS NULL OR s.status = :status)"
            + " AND (CAST(:warehouseId AS VARCHAR) IS NULL OR s.warehouse_id = :warehouseId)"
            + " AND (CAST(:kind AS VARCHAR) IS NULL OR s.kind = :kind) ";
    var statusName = status == null ? null : status.name();
    var kindName = kind == null ? null : kind.name();
    var total =
        jdbc.sql("SELECT count(*) FROM shipment s " + where)
            .param("status", statusName)
            .param("warehouseId", warehouseId)
            .param("kind", kindName)
            .query(Long.class)
            .single();
    var items =
        withDetails(
            jdbc.sql(SHIPMENT + where + "ORDER BY s.created_at, s.id LIMIT :size OFFSET :offset")
                .param("status", statusName)
                .param("warehouseId", warehouseId)
                .param("kind", kindName)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::header)
                .list());
    return new Page<>(items, page, size, total);
  }

  @Override
  public List<String> fromOtherWarehouses(Collection<String> warehouseIds) {
    return jdbc.sql(
            """
            SELECT id, warehouse_id FROM shipment WHERE warehouse_id NOT IN (:warehouseIds)
            ORDER BY created_at, id
            """)
        .param("warehouseIds", warehouseIds)
        .query((rs, row) -> rs.getString("id") + " (" + rs.getString("warehouse_id") + ")")
        .list();
  }

  private Optional<Shipment> one(JdbcClient.StatementSpec query) {
    return withDetails(query.query(this::header).list()).stream().findFirst();
  }

  /** A Shipment row, without its lines, history and Tracking events. */
  private record Header(
      UUID id,
      ShipmentKind kind,
      String rmaId,
      String orderId,
      String customerId,
      String warehouseId,
      ShipmentStatus status,
      long version,
      Instant createdAt,
      ShippingAddress address,
      ShippingOption option,
      String carrier,
      String trackingNumber,
      Instant deliveredAt,
      String holdReason) {}

  private Header header(ResultSet rs, int row) throws SQLException {
    var days = rs.getObject("estimated_days", Integer.class);
    return new Header(
        rs.getObject("id", UUID.class),
        ShipmentKind.valueOf(rs.getString("kind")),
        rs.getString("rma_id"),
        rs.getString("order_id"),
        rs.getString("customer_id"),
        rs.getString("warehouse_id"),
        ShipmentStatus.valueOf(rs.getString("status")),
        rs.getLong("version"),
        instant(rs, "created_at"),
        new ShippingAddress(
            rs.getString("recipient_name"),
            rs.getString("line1"),
            rs.getString("line2"),
            rs.getString("postcode"),
            rs.getString("city"),
            rs.getString("region"),
            rs.getString("country"),
            rs.getString("phone")),
        new ShippingOption(
            rs.getString("option_id"),
            rs.getString("option_carrier"),
            new Money(rs.getLong("cost_minor"), Currency.getInstance(rs.getString("currency"))),
            days),
        rs.getString("carrier"),
        rs.getString("tracking_number"),
        instant(rs, "delivered_at"),
        rs.getString("hold_reason"));
  }

  private List<Shipment> withDetails(List<Header> headers) {
    if (headers.isEmpty()) {
      return List.of();
    }
    var ids = headers.stream().map(Header::id).toList();
    var lines = new HashMap<UUID, List<ShipmentLine>>();
    var serials = new HashMap<String, List<String>>();
    jdbc.sql(
            """
            SELECT shipment_id, line_position, serial_number FROM shipment_serial_number
            WHERE shipment_id IN (:ids) ORDER BY shipment_id, line_position, unit
            """)
        .param("ids", ids)
        .query(
            (rs, row) ->
                serials
                    .computeIfAbsent(
                        rs.getString("shipment_id") + "/" + rs.getInt("line_position"),
                        k -> new ArrayList<>())
                    .add(rs.getString("serial_number")))
        .list();
    jdbc.sql(
            "SELECT * FROM shipment_line WHERE shipment_id IN (:ids) ORDER BY shipment_id,"
                + " position")
        .param("ids", ids)
        .query(
            (rs, row) -> {
              var shipmentId = rs.getObject("shipment_id", UUID.class);
              return lines
                  .computeIfAbsent(shipmentId, k -> new ArrayList<>())
                  .add(
                      new ShipmentLine(
                          rs.getString("variant_id"),
                          rs.getString("product_name"),
                          rs.getInt("quantity"),
                          SerialTracking.valueOf(rs.getString("serial_tracking")),
                          serials.getOrDefault(
                              shipmentId + "/" + rs.getInt("position"), List.of())));
            })
        .list();
    var history = new HashMap<UUID, List<StatusChange>>();
    jdbc.sql(
            "SELECT * FROM shipment_status_history WHERE shipment_id IN (:ids)"
                + " ORDER BY shipment_id, position")
        .param("ids", ids)
        .query(
            (rs, row) ->
                history
                    .computeIfAbsent(
                        rs.getObject("shipment_id", UUID.class), k -> new ArrayList<>())
                    .add(
                        new StatusChange(
                            ShipmentStatus.valueOf(rs.getString("status")),
                            instant(rs, "at"),
                            rs.getString("changed_by"))))
        .list();
    var tracking = new HashMap<UUID, List<TrackingEvent>>();
    jdbc.sql("SELECT * FROM tracking_event WHERE shipment_id IN (:ids)")
        .param("ids", ids)
        .query(
            (rs, row) ->
                tracking
                    .computeIfAbsent(
                        rs.getObject("shipment_id", UUID.class), k -> new ArrayList<>())
                    .add(
                        new TrackingEvent(
                            rs.getString("event_id"),
                            TrackingStatus.valueOf(rs.getString("status")),
                            instant(rs, "occurred_at"))))
        .list();
    return headers.stream()
        .map(
            h ->
                new Shipment(
                    h.id(),
                    h.kind(),
                    h.rmaId(),
                    h.orderId(),
                    h.customerId(),
                    h.warehouseId(),
                    h.address(),
                    h.option(),
                    lines.getOrDefault(h.id(), List.of()),
                    h.status(),
                    history.getOrDefault(h.id(), List.of()),
                    h.carrier(),
                    h.trackingNumber(),
                    tracking.getOrDefault(h.id(), List.of()),
                    h.deliveredAt(),
                    h.holdReason(),
                    h.createdAt(),
                    h.version()))
        .toList();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    var timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
