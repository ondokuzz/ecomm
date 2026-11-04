package com.ecomm.supportassistant.adapter.in.web;

import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.supportassistant.application.port.in.ConversationNotFoundException;
import com.ecomm.supportassistant.application.port.in.ConversationsUseCase;
import com.ecomm.supportassistant.domain.ConversationFullException;
import com.ecomm.supportassistant.domain.InvalidMessageException;
import java.net.URI;
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
   * the records it names. A full Conversation is a 409 {@code conversationFull}.
   */
  @PostMapping("/{id}/messages")
  MessageResponse send(
      CurrentCustomer customer, @PathVariable String id, @RequestBody MessageRequest request) {
    return MessageResponse.of(conversations.send(customer.id(), id, request.text()));
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

  @ExceptionHandler(InvalidMessageException.class)
  ProblemDetail invalid(InvalidMessageException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("reason", "invalidMessage");
    return problem;
  }
}
