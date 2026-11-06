package com.ecomm.supportassistant.adapter.out.redis;

import com.ecomm.supportassistant.application.port.out.TurnsInFlight;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Each claim is the key {@code support-assistant:in-flight:<Conversation ID>:<Idempotency-Key>},
 * set only if absent, with the time to live it is claimed for.
 */
@Component
class RedisTurnsInFlight implements TurnsInFlight {

  private final StringRedisTemplate redis;

  RedisTurnsInFlight(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean claim(String conversationId, String idempotencyKey, Duration atMost) {
    return Boolean.TRUE.equals(
        redis.opsForValue().setIfAbsent(key(conversationId, idempotencyKey), "", atMost));
  }

  @Override
  public void release(String conversationId, String idempotencyKey) {
    redis.delete(key(conversationId, idempotencyKey));
  }

  private static String key(String conversationId, String idempotencyKey) {
    return "support-assistant:in-flight:" + conversationId + ":" + idempotencyKey;
  }
}
