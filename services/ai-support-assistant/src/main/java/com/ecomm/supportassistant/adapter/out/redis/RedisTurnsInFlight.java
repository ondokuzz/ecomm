package com.ecomm.supportassistant.adapter.out.redis;

import com.ecomm.supportassistant.application.port.out.TurnsInFlight;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Each claim is the key {@code support-assistant:in-flight:<Conversation ID>}, set only if absent,
 * with the time to live it is claimed for. It holds a token of the claim's own, then, after a
 * space, the message's {@code Idempotency-Key}, if it has one.
 */
@Component
class RedisTurnsInFlight implements TurnsInFlight {

  /** Sets the claim only if absent: the claim the Conversation then holds, its own or another's. */
  private static final RedisScript<String> CLAIM =
      RedisScript.of(
          """
          redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])
          return redis.call('GET', KEYS[1])
          """,
          String.class);

  /** Deletes the claim only if it is still this one. */
  private static final RedisScript<Long> RELEASE =
      RedisScript.of(
          """
          if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
          return 0
          """,
          Long.class);

  private final StringRedisTemplate redis;

  RedisTurnsInFlight(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public Claim claim(String conversationId, String idempotencyKey, Duration atMost) {
    var token = UUID.randomUUID().toString();
    var holder = idempotencyKey == null ? token : token + " " + idempotencyKey;
    var held =
        redis.execute(
            CLAIM, List.of(key(conversationId)), holder, String.valueOf(atMost.toMillis()));
    if (holder.equals(held)) {
      return new Claim.Held(conversationId, holder);
    }
    var heldWithKey = held != null && held.length() > token.length();
    return heldWithKey && held.substring(token.length() + 1).equals(idempotencyKey)
        ? new Claim.HeldBySameKey()
        : new Claim.HeldByAnother();
  }

  @Override
  public void release(Claim.Held claim) {
    redis.execute(RELEASE, List.of(key(claim.conversationId())), claim.holder());
  }

  private static String key(String conversationId) {
    return "support-assistant:in-flight:" + conversationId;
  }
}
