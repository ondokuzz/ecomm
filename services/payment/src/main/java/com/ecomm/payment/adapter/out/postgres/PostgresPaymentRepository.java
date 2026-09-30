package com.ecomm.payment.adapter.out.postgres;

import com.ecomm.commons.money.Money;
import com.ecomm.payment.application.port.out.PaymentRepository;
import com.ecomm.payment.domain.Payment;
import com.ecomm.payment.domain.PaymentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Payments as rows of the {@code payment} table (see {@code db/migration}). */
@Component
class PostgresPaymentRepository implements PaymentRepository {

  private static final RowMapper<Payment> PAYMENT =
      (rs, row) ->
          new Payment(
              rs.getObject("id", UUID.class),
              rs.getString("customer_id"),
              rs.getString("order_id"),
              Money.of(rs.getLong("amount_minor"), rs.getString("currency")),
              PaymentStatus.valueOf(rs.getString("status")),
              rs.getString("decline_reason"),
              rs.getString("gateway_reference"));

  private final JdbcClient jdbc;

  PostgresPaymentRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void add(Payment payment) {
    jdbc.sql(
            """
            INSERT INTO payment
              (id, customer_id, order_id, amount_minor, currency, status, decline_reason,
               gateway_reference)
            VALUES
              (:id, :customerId, :orderId, :amountMinor, :currency, :status, :declineReason,
               :gatewayReference)
            """)
        .param("id", payment.id())
        .param("customerId", payment.customerId())
        .param("orderId", payment.orderId())
        .param("amountMinor", payment.amount().amountMinor())
        .param("currency", payment.amount().currency().getCurrencyCode())
        .param("status", payment.status().name())
        .param("declineReason", payment.declineReason())
        .param("gatewayReference", payment.gatewayReference())
        .update();
  }

  @Override
  public Optional<Payment> find(UUID id) {
    return jdbc.sql(
            """
            SELECT id, customer_id, order_id, amount_minor, currency, status, decline_reason,
                   gateway_reference
            FROM payment WHERE id = :id
            """)
        .param("id", id)
        .query(PAYMENT)
        .optional();
  }
}
