package com.ecomm.cart.adapter.out.redis;

import com.ecomm.cart.application.port.out.CartRepository;
import com.ecomm.cart.application.port.out.TimeSource;
import com.ecomm.cart.domain.AbandonedCart;
import com.ecomm.cart.domain.Cart;
import com.ecomm.cart.domain.CartItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Each Cart is one Redis hash, {@code cart:<Customer ID>}, mapping Variant ID to quantity. Every
 * change resets the hash's TTL to {@link Cart#LIFETIME} in the same script, so a Cart can never be
 * left without one.
 *
 * <p>The sorted set {@code carts:last-change} holds the Customer ID of each Cart not yet marked
 * abandoned, scored by its last change in epoch milliseconds, and every change writes it in the
 * same script as the hash. Marking a Cart abandoned takes it out of the set and sets the hash's
 * {@value #ABANDONED} field to that last change; the next change clears the field and puts the Cart
 * back. A Variant ID arrives as one path segment, so it never holds the field's {@code /}.
 *
 * <p>On start, a Cart in neither, such as one from before abandonment was detected, is added to the
 * set as changed then.
 */
@Component
class RedisCartRepository implements CartRepository {

  private static final Logger log = LoggerFactory.getLogger(RedisCartRepository.class);

  private static final String ABANDONED = "/abandoned";
  private static final String LAST_CHANGE = "carts:last-change";
  private static final String KEY_PREFIX = "cart:";

  private static final RedisScript<Long> PUT =
      RedisScript.of(
          """
          redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
          redis.call('HDEL', KEYS[1], '%s')
          redis.call('ZADD', KEYS[2], ARGV[4], ARGV[5])
          return redis.call('EXPIRE', KEYS[1], ARGV[3])
          """
              .formatted(ABANDONED),
          Long.class);

  // Removing the last Variant deletes the hash, which then leaves the set too. Removing one the
  // Cart doesn't hold changes nothing, so an abandoned Cart stays abandoned; it still keeps the
  // Cart.
  private static final RedisScript<Long> REMOVE =
      RedisScript.of(
          """
          if redis.call('HDEL', KEYS[1], ARGV[1]) == 0 then
            return redis.call('EXPIRE', KEYS[1], ARGV[2])
          end
          redis.call('HDEL', KEYS[1], '%s')
          if redis.call('EXISTS', KEYS[1]) == 0 then
            return redis.call('ZREM', KEYS[2], ARGV[4])
          end
          redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
          return redis.call('EXPIRE', KEYS[1], ARGV[2])
          """
              .formatted(ABANDONED),
          Long.class);

  private static final RedisScript<Long> DELETE =
      RedisScript.of(
          """
          redis.call('DEL', KEYS[1])
          return redis.call('ZREM', KEYS[2], ARGV[1])
          """,
          Long.class);

  // Marks only the abandonment that was read: a Cart changed since has a new score, or none.
  private static final RedisScript<Long> MARK_ABANDONED =
      RedisScript.of(
          """
          if tonumber(redis.call('ZSCORE', KEYS[2], ARGV[1])) ~= tonumber(ARGV[2]) then
            return 0
          end
          redis.call('ZREM', KEYS[2], ARGV[1])
          if redis.call('EXISTS', KEYS[1]) == 0 then
            return 0
          end
          redis.call('HSET', KEYS[1], '%s', ARGV[2])
          return 1
          """
              .formatted(ABANDONED),
          Long.class);

  // A candidate's last change and its hash, read together, so its lines are those of that change.
  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> READ_CANDIDATE =
      RedisScript.of(
          """
          local score = redis.call('ZSCORE', KEYS[2], ARGV[1])
          if not score then
            return {}
          end
          local cart = redis.call('HGETALL', KEYS[1])
          table.insert(cart, 1, score)
          return cart
          """,
          List.class);

  // A Cart that lapsed, or lost its last Variant some other way, leaves the set unabandoned.
  private static final RedisScript<Long> FORGET_IF_GONE =
      RedisScript.of(
          """
          if redis.call('EXISTS', KEYS[1]) == 1 then
            return 0
          end
          if tonumber(redis.call('ZSCORE', KEYS[2], ARGV[1])) ~= tonumber(ARGV[2]) then
            return 0
          end
          return redis.call('ZREM', KEYS[2], ARGV[1])
          """,
          Long.class);

  private static final RedisScript<Long> INDEX_IF_UNKNOWN =
      RedisScript.of(
          """
          if redis.call('HEXISTS', KEYS[1], '%s') == 1 then
            return 0
          end
          return redis.call('ZADD', KEYS[2], 'NX', ARGV[1], ARGV[2])
          """
              .formatted(ABANDONED),
          Long.class);

  private static final String LIFETIME_SECONDS = String.valueOf(Cart.LIFETIME.toSeconds());

  private final StringRedisTemplate redis;
  private final TimeSource time;

  RedisCartRepository(StringRedisTemplate redis, TimeSource time) {
    this.redis = redis;
    this.time = time;
  }

  @Override
  public Cart find(String customerId) {
    var entries = redis.<String, String>opsForHash().entries(key(customerId));
    return new Cart(
        entries.entrySet().stream()
            .filter(e -> !e.getKey().equals(ABANDONED))
            .map(e -> new CartItem(e.getKey(), Integer.parseInt(e.getValue())))
            .toList());
  }

  @Override
  public void put(String customerId, CartItem item, Instant changedAt) {
    redis.execute(
        PUT,
        List.of(key(customerId), LAST_CHANGE),
        item.variantId(),
        String.valueOf(item.quantity()),
        LIFETIME_SECONDS,
        millis(changedAt),
        customerId);
  }

  @Override
  public void remove(String customerId, String variantId, Instant changedAt) {
    redis.execute(
        REMOVE,
        List.of(key(customerId), LAST_CHANGE),
        variantId,
        LIFETIME_SECONDS,
        millis(changedAt),
        customerId);
  }

  @Override
  public void delete(String customerId) {
    redis.execute(DELETE, List.of(key(customerId), LAST_CHANGE), customerId);
  }

  @Override
  public List<AbandonedCart> idleSince(Instant cutoff, int limit) {
    var idle = new ArrayList<AbandonedCart>();
    var candidates =
        redis.opsForZSet().rangeByScore(LAST_CHANGE, 0, cutoff.toEpochMilli(), 0, limit);
    for (var customerId : candidates) {
      List<?> read =
          redis.execute(READ_CANDIDATE, List.of(key(customerId), LAST_CHANGE), customerId);
      if (read == null || read.isEmpty()) {
        continue; // changed and marked, or emptied, since the range was read
      }
      var score = (long) Double.parseDouble((String) read.getFirst());
      var items = new ArrayList<CartItem>();
      for (var i = 1; i + 1 < read.size(); i += 2) {
        var field = (String) read.get(i);
        if (!field.equals(ABANDONED)) {
          items.add(new CartItem(field, Integer.parseInt((String) read.get(i + 1))));
        }
      }
      var cart = new Cart(items);
      if (cart.items().isEmpty()) {
        redis.execute(
            FORGET_IF_GONE,
            List.of(key(customerId), LAST_CHANGE),
            customerId,
            String.valueOf(score));
      } else {
        idle.add(new AbandonedCart(customerId, cart, Instant.ofEpochMilli(score)));
      }
    }
    return idle;
  }

  @Override
  public boolean markAbandoned(AbandonedCart abandoned) {
    var marked =
        redis.execute(
            MARK_ABANDONED,
            List.of(key(abandoned.customerId()), LAST_CHANGE),
            abandoned.customerId(),
            millis(abandoned.lastChangedAt()));
    return marked != null && marked == 1;
  }

  /** Adds each Cart missing from the set, and not marked abandoned, as changed now. */
  @EventListener(ApplicationStartedEvent.class)
  void indexCartsChangedBeforeTheSetExisted() {
    var now = millis(time.now());
    var indexed = 0;
    var options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").type(DataType.HASH).build();
    try (var keys = redis.scan(options)) {
      while (keys.hasNext()) {
        var key = keys.next();
        var customerId = key.substring(KEY_PREFIX.length());
        Long added = redis.execute(INDEX_IF_UNKNOWN, List.of(key, LAST_CHANGE), now, customerId);
        if (added != null && added == 1) {
          indexed++;
        }
      }
    }
    if (indexed > 0) {
      log.info("Indexed {} Carts for abandonment, as changed now", indexed);
    }
  }

  private static String key(String customerId) {
    return KEY_PREFIX + customerId;
  }

  private static String millis(Instant instant) {
    return String.valueOf(instant.toEpochMilli());
  }
}
