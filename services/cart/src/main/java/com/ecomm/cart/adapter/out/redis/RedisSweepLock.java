package com.ecomm.cart.adapter.out.redis;

import com.ecomm.cart.application.port.out.SweepLock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * The key {@code carts:abandonment-sweep}, set only if absent and holding the sweep's own token, so
 * a sweep releases only its own hold. It lapses after {@link #HOLD} should an instance die holding
 * it.
 */
@Component
class RedisSweepLock implements SweepLock {

  private static final String KEY = "carts:abandonment-sweep";
  private static final Duration HOLD = Duration.ofMinutes(10);

  private static final RedisScript<Long> RELEASE =
      RedisScript.of(
          """
          if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
          end
          return 0
          """,
          Long.class);

  private final StringRedisTemplate redis;

  RedisSweepLock(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public <T> Optional<T> whileHeld(Supplier<T> sweep) {
    var token = UUID.randomUUID().toString();
    if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY, token, HOLD))) {
      return Optional.empty();
    }
    try {
      return Optional.of(sweep.get());
    } finally {
      redis.execute(RELEASE, List.of(KEY), token);
    }
  }
}
