package com.ecomm.commons.idempotency;

import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The {@code idempotency_key} table each service adds in its own migration. Every call but {@link
 * #prune} runs in the command's transaction.
 */
class IdempotencyKeys {

  /** What a request is, to tell a repeat from a different request that reuses its key. */
  record Request(String method, String path, String bodyHash) {}

  /** A response recorded with its key, to be replayed. */
  record Response(int status, String contentType, String location, byte[] body) {}

  sealed interface Claim {}

  /** The key is new: the command runs, and its response is recorded or the key released. */
  record Claimed() implements Claim {}

  /** The same request was answered before. */
  record Repeat(Response response) implements Claim {}

  /** The key came with a different request. */
  record Reused() implements Claim {}

  private final JdbcTemplate jdbc;

  IdempotencyKeys(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Claims {@code key} for {@code caller}. While another transaction holds it, this waits for that
   * one to end: once it commits, the request is a repeat or a reuse, and if it rolls back the key
   * is claimed here. A key pruned between the two statements is claimed again.
   */
  Claim claim(String caller, String key, Request request) {
    while (true) {
      var claim = tryClaim(caller, key, request);
      if (claim != null) {
        return claim;
      }
    }
  }

  /** The claim, or {@code null} when the key was there to insert but gone when read. */
  private Claim tryClaim(String caller, String key, Request request) {
    var inserted =
        jdbc.update(
            """
            INSERT INTO idempotency_key (caller, key, method, path, body_hash, created_at)
            VALUES (?, ?, ?, ?, ?, now())
            ON CONFLICT (caller, key) DO NOTHING
            """,
            caller,
            key,
            request.method(),
            request.path(),
            request.bodyHash());
    if (inserted == 1) {
      return new Claimed();
    }
    List<Claim> found =
        jdbc.query(
            """
        SELECT method, path, body_hash, response_status, response_type, response_location,
               response_body
        FROM idempotency_key WHERE caller = ? AND key = ?
        """,
            (rs, row) -> {
              var recorded =
                  new Request(
                      rs.getString("method"), rs.getString("path"), rs.getString("body_hash"));
              if (!recorded.equals(request)) {
                return new Reused();
              }
              return new Repeat(
                  new Response(
                      rs.getInt("response_status"),
                      rs.getString("response_type"),
                      rs.getString("response_location"),
                      rs.getBytes("response_body")));
            },
            caller,
            key);
    return found.isEmpty() ? null : found.getFirst();
  }

  void record(String caller, String key, Response response) {
    jdbc.update(
        """
        UPDATE idempotency_key
        SET response_status = ?, response_type = ?, response_location = ?, response_body = ?
        WHERE caller = ? AND key = ?
        """,
        response.status(),
        response.contentType(),
        response.location(),
        response.body(),
        caller,
        key);
  }

  /** Gives up a claimed key, so a repeat is evaluated afresh. */
  void release(String caller, String key) {
    jdbc.update("DELETE FROM idempotency_key WHERE caller = ? AND key = ?", caller, key);
  }

  /** Deletes the keys older than {@code age}, returning how many. */
  int prune(Duration age) {
    return jdbc.update(
        "DELETE FROM idempotency_key WHERE created_at < now() - make_interval(secs => ?)",
        age.toMillis() / 1000.0);
  }
}
