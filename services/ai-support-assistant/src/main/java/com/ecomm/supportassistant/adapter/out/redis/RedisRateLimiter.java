package com.ecomm.supportassistant.adapter.out.redis;

import com.ecomm.supportassistant.application.port.out.RateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Each subject's log is a sorted set, {@code support-assistant:rate:<subject>}, of what it did,
 * scored by the time in milliseconds. One script drops what has aged out of the longest window,
 * counts each window, and adds one more only if every limit allows it, so concurrent requests on
 * any instance can't both take the last one. The log lapses once its longest window has passed.
 */
@Component
class RedisRateLimiter implements RateLimiter {

  /**
   * KEYS[1] the log; ARGV[1] now, ARGV[2] the new entry, then each limit's count and window.
   * Answers 0 when it counted the entry, or else the 1-based index of the limit that frees up last,
   * and how many milliseconds until it does.
   */
  private static final RedisScript<List> TAKE =
      RedisScript.of(
          """
          local now = tonumber(ARGV[1])
          local longest = 0
          for i = 3, #ARGV, 2 do longest = math.max(longest, tonumber(ARGV[i + 1])) end
          redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - longest)
          local refused, wait = 0, 0
          for i = 3, #ARGV, 2 do
            local count, window = tonumber(ARGV[i]), tonumber(ARGV[i + 1])
            local since = '(' .. (now - window)
            local taken = redis.call('ZCOUNT', KEYS[1], since, '+inf')
            if taken >= count then
              -- The entry whose ageing out brings the window back under its count.
              local oldest = redis.call('ZRANGEBYSCORE', KEYS[1], since, '+inf', 'WITHSCORES',
                'LIMIT', taken - count, 1)
              local frees = tonumber(oldest[2]) + window - now
              if frees > wait then refused, wait = (i - 1) / 2, frees end
            end
          end
          if refused > 0 then return {refused, wait} end
          redis.call('ZADD', KEYS[1], now, ARGV[2])
          redis.call('PEXPIRE', KEYS[1], longest)
          return {0, 0}
          """,
          List.class);

  private final StringRedisTemplate redis;

  RedisRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public Optional<Refusal> take(String subject, List<Limit> limits, Instant at) {
    var args = new ArrayList<String>();
    args.add(String.valueOf(at.toEpochMilli()));
    args.add(at.toEpochMilli() + ":" + UUID.randomUUID());
    for (var limit : limits) {
      args.add(String.valueOf(limit.count()));
      args.add(String.valueOf(limit.per().toMillis()));
    }
    var answer = redis.execute(TAKE, List.of("support-assistant:rate:" + subject), args.toArray());
    var refused = ((Number) answer.get(0)).intValue();
    if (refused == 0) {
      return Optional.empty();
    }
    return Optional.of(
        new Refusal(
            limits.get(refused - 1), Duration.ofMillis(((Number) answer.get(1)).longValue())));
  }
}
