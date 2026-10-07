package com.ecomm.template;

import com.ecomm.commons.idempotency.IdempotentCommand;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only: commands that create a Greeting, declared idempotent the way a service's controller
 * declares its commands. Each creates a Greeting with a new ID in a transaction, as a use case does
 * through its {@code Transactions} port, so a computed response differs from a replayed one. A text
 * starting {@code slow} takes a second, one starting {@code boom} fails after its change with a
 * 500, and a text some Greeting already has is refused with a 409 that changes nothing. The refusal
 * is a response, not an exception, so its transaction commits rather than rolls back.
 */
@RestController
class GreetingCommands {

  record CreateGreeting(String text) {}

  record GreetingCreated(String id, String text) {}

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  GreetingCommands(JdbcTemplate jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  /** Requires an {@code Idempotency-Key}, as a command Orchestration calls does. */
  @PostMapping("/greetings")
  @IdempotentCommand
  ResponseEntity<?> create(@RequestBody CreateGreeting request) {
    return transactions
        .execute(status -> save(request.text()))
        .<ResponseEntity<?>>map(
            greeting ->
                ResponseEntity.created(URI.create("/greetings/" + greeting.id())).body(greeting))
        .orElseGet(GreetingCommands::exists);
  }

  /** Takes an {@code Idempotency-Key} when the caller sends one. */
  @PostMapping("/greetings/drafts")
  @IdempotentCommand(required = false)
  ResponseEntity<?> createDraft(@RequestBody CreateGreeting request) {
    return create(request);
  }

  /** The IDs of the Greetings with {@code text}. */
  @GetMapping("/greetings")
  List<String> withText(@RequestParam String text) {
    return jdbc.queryForList("SELECT id FROM greeting WHERE text = ?", String.class, text);
  }

  @DeleteMapping("/greetings/{id}")
  ResponseEntity<Void> delete(@PathVariable String id) {
    jdbc.update("DELETE FROM greeting WHERE id = ?", id);
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<ProblemDetail> exists() {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A Greeting has this text.");
    problem.setProperty("reason", "greetingExists");
    return ResponseEntity.of(problem).build();
  }

  private Optional<GreetingCreated> save(String text) {
    if (!withText(text).isEmpty()) {
      return Optional.empty();
    }
    var id = "greeting-" + UUID.randomUUID();
    jdbc.update("INSERT INTO greeting (id, text) VALUES (?, ?)", id, text);
    if (text.startsWith("slow")) {
      sleep();
    }
    if (text.startsWith("boom")) {
      throw new IllegalStateException("The command failed after its change");
    }
    return Optional.of(new GreetingCreated(id, text));
  }

  private static void sleep() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
