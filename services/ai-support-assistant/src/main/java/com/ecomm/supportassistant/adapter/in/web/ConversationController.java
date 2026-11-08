package com.ecomm.supportassistant.adapter.in.web;

import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.supportassistant.application.port.in.AssistantBusyException;
import com.ecomm.supportassistant.application.port.in.AssistantRateLimitedException;
import com.ecomm.supportassistant.application.port.in.AssistantUnavailableException;
import com.ecomm.supportassistant.application.port.in.ConversationBusyException;
import com.ecomm.supportassistant.application.port.in.ConversationNotFoundException;
import com.ecomm.supportassistant.application.port.in.ConversationsUseCase;
import com.ecomm.supportassistant.application.port.in.IdempotencyKeyInFlightException;
import com.ecomm.supportassistant.domain.ConversationFullException;
import com.ecomm.supportassistant.domain.IdempotencyKeyReusedException;
import com.ecomm.supportassistant.domain.InvalidIdempotencyKeyException;
import com.ecomm.supportassistant.domain.InvalidMessageException;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Customer's Conversations with the Support Assistant, with a {@code CUSTOMER} token; any other,
 * Staff's included, gets 403. Another Customer's Conversation, or one that ended or lapsed, is a
 * 404.
 */
@RestController
@RequestMapping("/conversations")
@PreAuthorize("hasRole('CUSTOMER')")
class ConversationController {

  private final ConversationsUseCase conversations;

  ConversationController(ConversationsUseCase conversations) {
    this.conversations = conversations;
  }

  /** 201 with the new, empty Conversation, and its URL in {@code Location}. */
  @PostMapping
  ResponseEntity<ConversationResponse> start(CurrentCustomer customer) {
    var conversation = conversations.start(customer.id());
    return ResponseEntity.created(URI.create("/conversations/" + conversation.id()))
        .body(ConversationResponse.of(conversation));
  }

  /** The transcript: the Customer's messages and the assistant's replies, oldest first. */
  @GetMapping("/{id}")
  ConversationResponse conversation(CurrentCustomer customer, @PathVariable String id) {
    return ConversationResponse.of(conversations.conversation(customer.id(), id));
  }

  /**
   * Adds the Customer's message, of 1 to 2,000 characters, and answers the assistant's reply with
   * the records it names. A full Conversation is a 409 {@code conversationFull}. A repeat with the
   * same {@code Idempotency-Key} answers the first reply, with {@code Idempotent-Replayed: true};
   * the key with another message is a 422 {@code idempotencyKeyReused}, and a repeat while the
   * first is still being answered a 409 {@code idempotencyKeyInFlight}. A Conversation runs one
   * turn at a time: any other message sent while one runs is a 409 {@code conversationBusy}, before
   * the provider is asked or the message counts against the Customer's limits. A Customer past
   * their limits is a 429 {@code assistantRateLimited}; a provider failing or refused by the
   * circuit breaker a 503 {@code assistantUnavailable}; the service past its provider ceiling a 503
   * {@code assistantBusy}. Each refusal past a limit says in {@code Retry-After} when to try again.
   */
  @PostMapping("/{id}/messages")
  ResponseEntity<MessageResponse> send(
      CurrentCustomer customer,
      @PathVariable String id,
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody MessageRequest request) {
    var sent = conversations.send(customer.id(), id, request.text(), idempotencyKey);
    var response = ResponseEntity.ok();
    if (sent.replayed()) {
      response.header("Idempotent-Replayed", "true");
    }
    return response.body(MessageResponse.of(sent.reply()));
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> end(CurrentCustomer customer, @PathVariable String id) {
    conversations.end(customer.id(), id);
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(ConversationNotFoundException.class)
  ProblemDetail notFound(ConversationNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(ConversationFullException.class)
  ProblemDetail full(ConversationFullException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reason", "conversationFull");
    return problem;
  }

  @ExceptionHandler(AssistantRateLimitedException.class)
  ResponseEntity<ProblemDetail> rateLimited(AssistantRateLimitedException e) {
    return refused(HttpStatus.TOO_MANY_REQUESTS, "assistantRateLimited", e, e.retryAfter());
  }

  @ExceptionHandler(AssistantBusyException.class)
  ResponseEntity<ProblemDetail> busy(AssistantBusyException e) {
    return refused(HttpStatus.SERVICE_UNAVAILABLE, "assistantBusy", e, e.retryAfter());
  }

  @ExceptionHandler(AssistantUnavailableException.class)
  ResponseEntity<ProblemDetail> unavailable(AssistantUnavailableException e) {
    return refused(HttpStatus.SERVICE_UNAVAILABLE, "assistantUnavailable", e, null);
  }

  /** A problem with its {@code reason}, and, after {@code retryAfter}, when to try again. */
  private static ResponseEntity<ProblemDetail> refused(
      HttpStatus status, String reason, Exception e, Duration retryAfter) {
    var problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
    problem.setProperty("reason", reason);
    var response = ResponseEntity.status(status);
    if (retryAfter != null) {
      // Whole seconds, rounded up, so a Customer who waits that long is let through.
      var seconds = retryAfter.plusMillis(999).toSeconds();
      response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
    }
    return response.body(problem);
  }

  @ExceptionHandler(InvalidIdempotencyKeyException.class)
  ProblemDetail invalidKey(InvalidIdempotencyKeyException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("reason", "idempotencyKeyInvalid");
    return problem;
  }

  @ExceptionHandler(IdempotencyKeyInFlightException.class)
  ProblemDetail inFlight(IdempotencyKeyInFlightException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reason", "idempotencyKeyInFlight");
    return problem;
  }

  @ExceptionHandler(ConversationBusyException.class)
  ProblemDetail conversationBusy(ConversationBusyException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reason", "conversationBusy");
    return problem;
  }

  @ExceptionHandler(IdempotencyKeyReusedException.class)
  ProblemDetail reusedKey(IdempotencyKeyReusedException e) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    problem.setProperty("reason", "idempotencyKeyReused");
    return problem;
  }

  @ExceptionHandler(InvalidMessageException.class)
  ProblemDetail invalid(InvalidMessageException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("reason", "invalidMessage");
    return problem;
  }
}
