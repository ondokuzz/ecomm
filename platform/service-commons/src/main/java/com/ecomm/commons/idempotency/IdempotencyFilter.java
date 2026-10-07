package com.ecomm.commons.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Makes each {@link IdempotentCommand} idempotent by its {@code Idempotency-Key} header. It runs
 * after the security filter chain, so the caller is known.
 *
 * <p>The command runs in a transaction this filter opens, which the use case's own transaction
 * joins. The key is claimed in it first: a concurrent request with the same key waits on that claim
 * until this transaction ends. A 2xx response is recorded with the key before it commits, and is
 * sent only once it has. Any other response gives the key up, so a repeat is evaluated afresh; the
 * command's change commits or rolls back as it would have without a key.
 */
class IdempotencyFilter extends OncePerRequestFilter {

  static final String KEY_HEADER = "Idempotency-Key";
  static final String REPLAYED_HEADER = "Idempotent-Replayed";

  private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
  private static final int MAX_KEY_LENGTH = 255;

  private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;
  private final IdempotencyKeys keys;
  private final PlatformTransactionManager transactionManager;
  private final HandlerExceptionResolver problems;

  IdempotencyFilter(
      ObjectProvider<RequestMappingHandlerMapping> handlerMapping,
      IdempotencyKeys keys,
      PlatformTransactionManager transactionManager,
      HandlerExceptionResolver problems) {
    this.handlerMapping = handlerMapping;
    this.keys = keys;
    this.transactionManager = transactionManager;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var command = commandOf(request);
    var caller = caller();
    if (command == null || caller == null) {
      chain.doFilter(request, response);
      return;
    }
    var key = request.getHeader(KEY_HEADER);
    if (key == null) {
      if (command.required()) {
        refuse(request, response, IdempotencyKeyException.required());
      } else {
        chain.doFilter(request, response);
      }
      return;
    }
    if (!isValid(key)) {
      refuse(request, response, IdempotencyKeyException.invalid());
      return;
    }
    var body = new CachedBodyRequest(request);
    var fingerprint =
        new IdempotencyKeys.Request(request.getMethod(), request.getRequestURI(), sha256(body));
    var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
    try {
      switch (keys.claim(caller, key, fingerprint)) {
        case IdempotencyKeys.Repeat(var recorded) -> {
          transactionManager.commit(transaction);
          log.info("Replayed {} {} for key {}", request.getMethod(), request.getRequestURI(), key);
          replay(recorded, response);
        }
        case IdempotencyKeys.Reused() -> {
          transactionManager.rollback(transaction);
          refuse(request, response, IdempotencyKeyException.reused());
        }
        case IdempotencyKeys.Claimed() -> run(chain, body, response, caller, key, transaction);
      }
    } finally {
      if (!transaction.isCompleted()) {
        transactionManager.rollback(transaction);
      }
    }
  }

  private void run(
      FilterChain chain,
      CachedBodyRequest request,
      HttpServletResponse response,
      String caller,
      String key,
      TransactionStatus transaction)
      throws ServletException, IOException {
    var cached = new ContentCachingResponseWrapper(response);
    chain.doFilter(request, cached);
    if (transaction.isRollbackOnly()) {
      transactionManager.rollback(transaction);
    } else {
      if (cached.getStatus() / 100 == 2) {
        keys.record(
            caller,
            key,
            new IdempotencyKeys.Response(
                cached.getStatus(),
                cached.getContentType(),
                cached.getHeader(HttpHeaders.LOCATION),
                cached.getContentAsByteArray()));
      } else {
        keys.release(caller, key);
      }
      transactionManager.commit(transaction);
    }
    cached.copyBodyToResponse();
  }

  private static void replay(IdempotencyKeys.Response recorded, HttpServletResponse response)
      throws IOException {
    response.setStatus(recorded.status());
    if (recorded.contentType() != null) {
      response.setContentType(recorded.contentType());
    }
    if (recorded.location() != null) {
      response.setHeader(HttpHeaders.LOCATION, recorded.location());
    }
    response.setHeader(REPLAYED_HEADER, "true");
    if (recorded.body() != null) {
      response.setContentLength(recorded.body().length);
      response.getOutputStream().write(recorded.body());
    }
  }

  private void refuse(
      HttpServletRequest request, HttpServletResponse response, IdempotencyKeyException e) {
    problems.resolveException(request, response, null, e);
  }

  /** The handler method's declaration, or {@code null} when it isn't an idempotent command. */
  private IdempotentCommand commandOf(HttpServletRequest request) {
    var mapping = handlerMapping.getIfAvailable();
    if (mapping == null) {
      return null;
    }
    // The handler mapping matches on a parsed path, which the DispatcherServlet parses again.
    ServletRequestPathUtils.parseAndCache(request);
    try {
      var handler = mapping.getHandler(request);
      return handler != null && handler.getHandler() instanceof HandlerMethod method
          ? method.getMethodAnnotation(IdempotentCommand.class)
          : null;
    } catch (Exception e) {
      // No handler takes the request, such as a method the path doesn't allow. The
      // DispatcherServlet answers it with its own error.
      return null;
    } finally {
      ServletRequestPathUtils.clearParsedRequestPath(request);
    }
  }

  /**
   * Whom keys are scoped to: a service's client, from a client-credentials token's {@code
   * client_id}, or else the token's {@code sub}, such as a Customer.
   */
  private static String caller() {
    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken token) {
      var client = token.getToken().getClaimAsString("client_id");
      return client != null ? client : token.getToken().getSubject();
    }
    return null;
  }

  private static boolean isValid(String key) {
    return !key.isEmpty()
        && key.length() <= MAX_KEY_LENGTH
        && key.chars().allMatch(c -> c >= 0x20 && c <= 0x7e);
  }

  private static String sha256(CachedBodyRequest request) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.body()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
