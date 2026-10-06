package com.ecomm.checkoutpricing.adapter.out.redis;

import com.ecomm.checkoutpricing.application.port.out.CheckoutSessionRepository;
import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.PricedCart;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.commons.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Each Checkout Session is one JSON string, {@code checkout-session:<ID>}, and its Customer's
 * pointer to it is {@code checkout-customer:<Customer ID>}, holding the ID. Both carry the time to
 * live they are saved with, and Redis drops them when it runs out.
 */
@Component
class RedisCheckoutSessionRepository implements CheckoutSessionRepository {

  private static final RedisScript<Long> SAVE =
      RedisScript.of(
          """
          redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
          redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
          return 1
          """,
          Long.class);

  // A newer session may already have taken over the pointer; that one stays.
  private static final RedisScript<Long> DELETE =
      RedisScript.of(
          """
          if redis.call('GET', KEYS[2]) == ARGV[1] then redis.call('DEL', KEYS[2]) end
          return redis.call('DEL', KEYS[1])
          """,
          Long.class);

  // Only a session still kept, for the time it has left: one that was replaced or has lapsed
  // stays gone.
  private static final RedisScript<Long> REPLACE =
      RedisScript.of(
          """
          if redis.call('SET', KEYS[1], ARGV[1], 'XX', 'KEEPTTL') then return 1 end
          return 0
          """,
          Long.class);

  private final StringRedisTemplate redis;
  private final JsonMapper json;

  RedisCheckoutSessionRepository(StringRedisTemplate redis, JsonMapper json) {
    this.redis = redis;
    this.json = json;
  }

  /** A session as it is stored; decoupled from the domain so the domain can change freely. */
  private record Stored(
      String id,
      String customerId,
      List<StoredLine> lines,
      List<StoredDiscount> discounts,
      StoredMoney tax,
      String reservationId,
      Instant expiresAt) {

    static Stored of(CheckoutSession session) {
      return new Stored(
          session.id(),
          session.customerId(),
          session.cart().lines().stream()
              .map(
                  l ->
                      new StoredLine(
                          l.variantId(),
                          l.sku(),
                          l.category(),
                          l.quantity(),
                          StoredMoney.of(l.unitPrice())))
              .toList(),
          session.discounts().stream().map(StoredDiscount::of).toList(),
          StoredMoney.of(session.tax()),
          session.reservationId(),
          session.expiresAt());
    }

    /**
     * The session, unless it was saved before sessions held their lines' Products and every
     * Discount: without them it can't be evaluated again, so it reads as none, and the Customer
     * starts checkout afresh. Its Reservation lapses by itself.
     */
    Optional<CheckoutSession> toSession() {
      if (discounts == null
          || lines.stream().anyMatch(l -> l.sku() == null || l.category() == null)) {
        return Optional.empty();
      }
      return Optional.of(
          new CheckoutSession(
              id,
              customerId,
              new PricedCart(
                  lines.stream()
                      .map(
                          l ->
                              new PricedLine(
                                  l.variantId(),
                                  l.sku(),
                                  l.category(),
                                  l.quantity(),
                                  l.unitPrice().toMoney()))
                      .toList()),
              discounts.stream().map(StoredDiscount::toDiscount).toList(),
              tax.toMoney(),
              reservationId,
              expiresAt));
    }
  }

  private record StoredLine(
      String variantId, String sku, String category, int quantity, StoredMoney unitPrice) {}

  private record StoredDiscount(
      String source,
      String couponCode,
      String campaignId,
      String campaignName,
      StoredMoney amount) {

    static StoredDiscount of(Discount discount) {
      return new StoredDiscount(
          discount.source().name(),
          discount.couponCode(),
          discount.campaignId(),
          discount.campaignName(),
          StoredMoney.of(discount.amount()));
    }

    Discount toDiscount() {
      return new Discount(
          Discount.Source.valueOf(source), couponCode, campaignId, campaignName, amount.toMoney());
    }
  }

  private record StoredMoney(long amountMinor, String currency) {

    static StoredMoney of(Money money) {
      return new StoredMoney(money.amountMinor(), money.currency().getCurrencyCode());
    }

    Money toMoney() {
      return Money.of(amountMinor, currency);
    }
  }

  @Override
  public void save(CheckoutSession session, Duration timeToLive) {
    redis.execute(
        SAVE,
        List.of(key(session.id()), customerKey(session.customerId())),
        json.writeValueAsString(Stored.of(session)),
        session.id(),
        String.valueOf(timeToLive.toMillis()));
  }

  @Override
  public boolean replace(CheckoutSession session) {
    var replaced =
        redis.execute(
            REPLACE, List.of(key(session.id())), json.writeValueAsString(Stored.of(session)));
    return replaced != null && replaced == 1;
  }

  @Override
  public Optional<CheckoutSession> find(String sessionId) {
    return Optional.ofNullable(redis.opsForValue().get(key(sessionId)))
        .flatMap(value -> json.readValue(value, Stored.class).toSession());
  }

  @Override
  public Optional<CheckoutSession> findByCustomer(String customerId) {
    return Optional.ofNullable(redis.opsForValue().get(customerKey(customerId)))
        .flatMap(this::find);
  }

  @Override
  public void delete(CheckoutSession session) {
    redis.execute(
        DELETE, List.of(key(session.id()), customerKey(session.customerId())), session.id());
  }

  private static String key(String sessionId) {
    return "checkout-session:" + sessionId;
  }

  private static String customerKey(String customerId) {
    return "checkout-customer:" + customerId;
  }
}
