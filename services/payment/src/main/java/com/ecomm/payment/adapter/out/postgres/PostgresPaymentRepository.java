package com.ecomm.payment.adapter.out.postgres;

import com.ecomm.commons.money.Money;
import com.ecomm.payment.application.port.out.PaymentRepository;
import com.ecomm.payment.domain.GatewaySettlement;
import com.ecomm.payment.domain.Payment;
import com.ecomm.payment.domain.PaymentTransaction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Payments as rows of the {@code payment} table, with their Payment transactions as rows of {@code
 * payment_transaction}, which are only ever inserted (see {@code db/migration}). {@code
 * payment.status} is written with each transaction, from the Payment's own status. Each Gateway
 * webhook received is a row of {@code gateway_webhook}, keyed by its event ID.
 */
@Component
class PostgresPaymentRepository implements PaymentRepository {

  private static final String PAYMENT_COLUMNS =
      "p.id, p.customer_id, p.order_id, p.amount_minor, p.currency";

  private final JdbcClient jdbc;

  PostgresPaymentRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void add(Payment payment) {
    jdbc.sql(
            """
            INSERT INTO payment (id, customer_id, order_id, amount_minor, currency, status)
            VALUES (:id, :customerId, :orderId, :amountMinor, :currency, :status)
            """)
        .param("id", payment.id())
        .param("customerId", payment.customerId())
        .param("orderId", payment.orderId())
        .param("amountMinor", payment.amount().amountMinor())
        .param("currency", payment.amount().currency().getCurrencyCode())
        .param("status", payment.status().name())
        .update();
    var transactions = payment.transactions();
    for (var position = 0; position < transactions.size(); position++) {
      insert(payment.id(), position, transactions.get(position));
    }
  }

  @Override
  public void recordLatestTransaction(Payment payment) {
    var transactions = payment.transactions();
    insert(payment.id(), transactions.size() - 1, transactions.getLast());
    jdbc.sql("UPDATE payment SET status = :status WHERE id = :id")
        .param("status", payment.status().name())
        .param("id", payment.id())
        .update();
  }

  @Override
  public Optional<Payment> find(UUID id) {
    return withTransactions(
            jdbc.sql("SELECT " + PAYMENT_COLUMNS + " FROM payment p WHERE p.id = :id")
                .param("id", id)
                .query(this::header)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Payment> lockToChange(UUID id) {
    return withTransactions(
            jdbc.sql("SELECT " + PAYMENT_COLUMNS + " FROM payment p WHERE p.id = :id FOR UPDATE")
                .param("id", id)
                .query(this::header)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Payment> lockToChangeByGatewayReference(String reference) {
    return withTransactions(
            jdbc.sql(
                    "SELECT "
                        + PAYMENT_COLUMNS
                        + """
                         FROM payment p
                        JOIN payment_transaction t ON t.payment_id = p.id AND t.position = 0
                        WHERE t.gateway_reference = :reference
                        FOR UPDATE OF p
                        """)
                .param("reference", reference)
                .query(this::header)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  public boolean addReceivedWebhook(UUID paymentId, GatewaySettlement settlement, Instant at) {
    return jdbc.sql(
                """
                INSERT INTO gateway_webhook
                  (event_id, payment_id, gateway_reference, outcome, decline_reason, received_at)
                VALUES
                  (:eventId, :paymentId, :reference, :outcome, :declineReason, :receivedAt)
                ON CONFLICT (event_id) DO NOTHING
                """)
            .param("eventId", settlement.eventId())
            .param("paymentId", paymentId)
            .param("reference", settlement.reference())
            .param("outcome", settlement.answer().outcome().name())
            .param("declineReason", settlement.answer().declineReason())
            .param("receivedAt", Timestamp.from(at))
            .update()
        == 1;
  }

  @Override
  public List<Payment> findByOrder(String orderId) {
    return withTransactions(
        jdbc.sql(
                "SELECT "
                    + PAYMENT_COLUMNS
                    + """
                     FROM payment p
                    JOIN payment_transaction t ON t.payment_id = p.id AND t.position = 0
                    WHERE p.order_id = :orderId
                    ORDER BY t.at DESC, p.id
                    """)
            .param("orderId", orderId)
            .query(this::header)
            .list());
  }

  @Override
  public List<Payment> lockAwaitingBackfillEvent(int limit) {
    return withTransactions(
        jdbc.sql(
                "SELECT "
                    + PAYMENT_COLUMNS
                    + """
                     FROM payment p WHERE p.id IN (
                      SELECT payment_id FROM payment_awaiting_backfill_event
                      ORDER BY payment_id LIMIT :limit FOR UPDATE SKIP LOCKED)
                    ORDER BY p.id
                    """)
            .param("limit", limit)
            .query(this::header)
            .list());
  }

  @Override
  public void markBackfillPublished(List<UUID> ids) {
    if (ids.isEmpty()) {
      return;
    }
    jdbc.sql("DELETE FROM payment_awaiting_backfill_event WHERE payment_id IN (:ids)")
        .param("ids", ids)
        .update();
  }

  private void insert(UUID paymentId, int position, PaymentTransaction transaction) {
    jdbc.sql(
            """
            INSERT INTO payment_transaction
              (payment_id, position, kind, amount_minor, currency, outcome, gateway_reference,
               decline_reason, gateway_event_id, at, backfilled)
            VALUES
              (:paymentId, :position, :kind, :amountMinor, :currency, :outcome, :gatewayReference,
               :declineReason, :gatewayEventId, :at, :backfilled)
            """)
        .param("paymentId", paymentId)
        .param("position", position)
        .param("kind", transaction.kind().name())
        .param("amountMinor", transaction.amount().amountMinor())
        .param("currency", transaction.amount().currency().getCurrencyCode())
        .param("outcome", transaction.outcome().name())
        .param("gatewayReference", transaction.gatewayReference())
        .param("declineReason", transaction.declineReason())
        .param("gatewayEventId", transaction.gatewayEventId())
        .param("at", Timestamp.from(transaction.at()))
        .param("backfilled", transaction.backfilled())
        .update();
  }

  /** A Payment's own columns, before its transactions are read. */
  private record Header(UUID id, String customerId, String orderId, Money amount) {}

  private Header header(ResultSet rs, int row) throws SQLException {
    return new Header(
        rs.getObject("id", UUID.class),
        rs.getString("customer_id"),
        rs.getString("order_id"),
        Money.of(rs.getLong("amount_minor"), rs.getString("currency")));
  }

  /** The Payments, in the same order, each with its transactions oldest first. */
  private List<Payment> withTransactions(List<Header> headers) {
    if (headers.isEmpty()) {
      return List.of();
    }
    var byPayment = new HashMap<UUID, List<PaymentTransaction>>();
    jdbc.sql(
            """
            SELECT payment_id, kind, amount_minor, currency, outcome, gateway_reference,
                   decline_reason, gateway_event_id, at, backfilled
            FROM payment_transaction
            WHERE payment_id IN (:ids)
            ORDER BY payment_id, position
            """)
        .param("ids", headers.stream().map(Header::id).toList())
        .query(
            rs -> {
              byPayment
                  .computeIfAbsent(rs.getObject("payment_id", UUID.class), id -> new ArrayList<>())
                  .add(
                      new PaymentTransaction(
                          PaymentTransaction.Kind.valueOf(rs.getString("kind")),
                          Money.of(rs.getLong("amount_minor"), rs.getString("currency")),
                          PaymentTransaction.Outcome.valueOf(rs.getString("outcome")),
                          rs.getString("gateway_reference"),
                          rs.getString("decline_reason"),
                          rs.getString("gateway_event_id"),
                          rs.getTimestamp("at").toInstant(),
                          rs.getBoolean("backfilled")));
            });
    return headers.stream()
        .map(
            h ->
                new Payment(h.id(), h.customerId(), h.orderId(), h.amount(), byPayment.get(h.id())))
        .toList();
  }
}
