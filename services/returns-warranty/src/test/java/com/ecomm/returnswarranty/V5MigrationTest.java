package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The V5 migration, on a database of its own migrated to Sprint 5's V4 and given Sprint 5's rows:
 * approved RMAs gain their Send-back deadline, and RMA decisions stay the first entries of the RMA
 * history. The migration's subject is the schema, so this test reads tables, as no other does.
 */
class V5MigrationTest {

  private static final PostgreSQLContainer POSTGRES = ReturnsWarrantyApiTest.POSTGRES;

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbc;

  @BeforeEach
  void sprint5Database() throws Exception {
    var database = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    try (var admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      admin.createStatement().execute("CREATE DATABASE " + database);
    }
    dataSource =
        new SingleConnectionDataSource(
            POSTGRES.getJdbcUrl().replace("/returns_warranty", "/" + database),
            POSTGRES.getUsername(),
            POSTGRES.getPassword(),
            true);
    Flyway.configure().dataSource(dataSource).target("4").load().migrate();
    jdbc = JdbcClient.create(dataSource);
  }

  @AfterEach
  void close() {
    dataSource.destroy();
  }

  @Test
  void approvedRmasGainTheirSendBackDeadlineAndKeepTheirHistory() {
    // Both approved on 1 October: one's window ends on 20 October, later than 14 days after; the
    // other's ended on 5 October, so 15 October is its deadline.
    var approved = UUID.randomUUID();
    var windowEndedSooner = UUID.randomUUID();
    var requested = UUID.randomUUID();
    insert(approved, "APPROVED", "2026-10-20T12:00:00Z");
    insert(windowEndedSooner, "APPROVED", "2026-10-05T12:00:00Z");
    insert(requested, "REQUESTED", "2026-10-10T12:00:00Z");
    for (var id : new UUID[] {approved, windowEndedSooner}) {
      jdbc.sql(
              """
              INSERT INTO rma_decision (rma_id, position, status, decided_by, decided_at, note)
              VALUES (:id, 0, 'APPROVED', 'staff-7', :at, 'Send it back')
              """)
          .param("id", id)
          .param("at", Timestamp.from(Instant.parse("2026-10-01T12:00:00Z")))
          .update();
    }

    Flyway.configure().dataSource(dataSource).load().migrate();

    assertThat(
            jdbc.sql(
                    """
                    SELECT position || ':' || status || ':' || changed_by || ':' || note
                    FROM rma_history WHERE rma_id = :id ORDER BY position
                    """)
                .param("id", approved)
                .query(String.class)
                .list())
        .containsExactly("0:APPROVED:staff-7:Send it back");
    assertThat(sendBackBy(approved)).isEqualTo("2026-10-20T12:00:00Z");
    assertThat(sendBackBy(windowEndedSooner)).isEqualTo("2026-10-15T12:00:00Z");
    assertThat(sendBackBy(requested)).isNull();
  }

  private void insert(UUID id, String status, String windowEndsAt) {
    Sprint5Rmas.insert(jdbc, id, status, Instant.parse(windowEndsAt), "order-" + id, "V-1");
  }

  private String sendBackBy(UUID id) {
    return jdbc.sql("SELECT send_back_by FROM rma WHERE id = :id")
        .param("id", id)
        .query((rs, row) -> rs.getTimestamp(1))
        .optional()
        .map(t -> t == null ? null : t.toInstant().toString())
        .orElse(null);
  }
}
