package com.ecomm.cart.adapter.out.redis;

import com.ecomm.cart.application.port.out.CartRepository;
import com.ecomm.cart.domain.Cart;
import com.ecomm.cart.domain.CartItem;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Each Cart is one Redis hash, {@code cart:<Customer ID>}, mapping Variant ID to quantity. Every
 * change resets the hash's TTL to {@link Cart#LIFETIME} in the same script, so a Cart can never be
 * left without one.
 */
@Component
class RedisCartRepository implements CartRepository {

  private static final RedisScript<Long> PUT =
      RedisScript.of(
          """
          redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
          return redis.call('EXPIRE', KEYS[1], ARGV[3])
          """,
          Long.class);

  // Removing the last Variant deletes the hash, and EXPIRE on a missing key does nothing.
  private static final RedisScript<Long> REMOVE =
      RedisScript.of(
          """
          redis.call('HDEL', KEYS[1], ARGV[1])
          return redis.call('EXPIRE', KEYS[1], ARGV[2])
          """,
          Long.class);

  private static final String LIFETIME_SECONDS = String.valueOf(Cart.LIFETIME.toSeconds());

  private final StringRedisTemplate redis;

  RedisCartRepository(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public Cart find(String customerId) {
    var entries = redis.<String, String>opsForHash().entries(key(customerId));
    return new Cart(
        entries.entrySet().stream()
            .map(e -> new CartItem(e.getKey(), Integer.parseInt(e.getValue())))
            .toList());
  }

  @Override
  public void put(String customerId, CartItem item) {
    redis.execute(
        PUT,
        List.of(key(customerId)),
        item.variantId(),
        String.valueOf(item.quantity()),
        LIFETIME_SECONDS);
  }

  @Override
  public void remove(String customerId, String variantId) {
    redis.execute(REMOVE, List.of(key(customerId)), variantId, LIFETIME_SECONDS);
  }

  @Override
  public void delete(String customerId) {
    redis.delete(key(customerId));
  }

  private static String key(String customerId) {
    return "cart:" + customerId;
  }
}
