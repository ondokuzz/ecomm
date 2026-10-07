package com.ecomm.commons.idempotency;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Deletes the keys older than {@link IdempotencyProperties#keepFor()} on a fixed interval, starting
 * at once. It runs only in a service that declares an {@link IdempotentCommand}, since only such a
 * service has the table.
 */
class IdempotencyKeyPruner implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPruner.class);

  private final IdempotencyKeys keys;
  private final IdempotencyProperties properties;
  private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;
  private ScheduledExecutorService scheduler;

  IdempotencyKeyPruner(
      IdempotencyKeys keys,
      IdempotencyProperties properties,
      ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
    this.keys = keys;
    this.properties = properties;
    this.handlerMapping = handlerMapping;
  }

  @Override
  public void start() {
    if (!declaresCommands()) {
      return;
    }
    var interval = properties.pruneEvery().toMillis();
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("idempotency-key-pruner").daemon().factory());
    scheduler.scheduleWithFixedDelay(this::prune, 0, interval, TimeUnit.MILLISECONDS);
  }

  private boolean declaresCommands() {
    var mapping = handlerMapping.getIfAvailable();
    return mapping != null
        && mapping.getHandlerMethods().values().stream()
            .anyMatch(method -> method.hasMethodAnnotation(IdempotentCommand.class));
  }

  private void prune() {
    try {
      var pruned = keys.prune(properties.keepFor());
      if (pruned > 0) {
        log.info("Pruned {} idempotency keys older than {}", pruned, properties.keepFor());
      }
    } catch (RuntimeException e) {
      log.warn("Pruning idempotency keys failed", e);
    }
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
  }

  @Override
  public boolean isRunning() {
    return scheduler != null;
  }
}
