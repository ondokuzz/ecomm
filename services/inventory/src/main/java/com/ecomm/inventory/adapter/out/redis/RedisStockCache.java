package com.ecomm.inventory.adapter.out.redis;

import com.ecomm.inventory.application.port.out.CachedStock;
import com.ecomm.inventory.application.port.out.StockCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Each Variant's entry is one JSON string, {@code inventory:stock:<Variant ID>}, in the stack's
 * Redis, living until it stops being fresh. Redis being unreachable is logged at warn and counted,
 * and reads as a miss.
 *
 * <p>Counts {@code ecomm.stock.cache.hits} and {@code ecomm.stock.cache.misses} per Variant looked
 * up, by a Stock read or a Reservation's check, and {@code ecomm.stock.cache.errors} per call to
 * Redis that failed.
 */
@Component
class RedisStockCache implements StockCache {

  private static final Logger log = LoggerFactory.getLogger(RedisStockCache.class);

  private static final String KEY_PREFIX = "inventory:stock:";

  // ARGV holds each key's entry, then its time to live in milliseconds. An entry of the same
  // version is written again: it was worked out later, so it is at least as fresh.
  private static final RedisScript<Long> PUT =
      RedisScript.of(
          """
          for i, key in ipairs(KEYS) do
            local entry = ARGV[2 * i - 1]
            local held = redis.call('GET', key)
            if not held or cjson.decode(held).version <= cjson.decode(entry).version then
              redis.call('SET', key, entry, 'PX', ARGV[2 * i])
            end
          end
          return 1
          """,
          Long.class);

  /** An entry as it is stored; decoupled from the port so either can change freely. */
  private record Stored(
      String variantId,
      int onHand,
      int reserved,
      int available,
      boolean stocked,
      long version,
      Instant freshUntil) {

    static Stored of(CachedStock entry) {
      return new Stored(
          entry.variantId(),
          entry.onHand(),
          entry.reserved(),
          entry.available(),
          entry.stocked(),
          entry.version(),
          entry.freshUntil());
    }

    CachedStock toEntry() {
      return new CachedStock(variantId, onHand, reserved, stocked, version, freshUntil);
    }
  }

  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final Counter hits;
  private final Counter misses;
  private final Counter errors;

  RedisStockCache(StringRedisTemplate redis, JsonMapper json, MeterRegistry registry) {
    this.redis = redis;
    this.json = json;
    this.hits =
        Counter.builder("ecomm.stock.cache.hits")
            .description("Variants whose Stock was read from the Stock cache")
            .register(registry);
    this.misses =
        Counter.builder("ecomm.stock.cache.misses")
            .description("Variants whose Stock the Stock cache didn't hold fresh")
            .register(registry);
    this.errors =
        Counter.builder("ecomm.stock.cache.errors")
            .description("Calls to the Stock cache's Redis that failed")
            .register(registry);
  }

  @Override
  public Map<String, CachedStock> get(Collection<String> variantIds, Instant now) {
    var ids = List.copyOf(variantIds);
    var found = new LinkedHashMap<String, CachedStock>();
    if (ids.isEmpty()) {
      return found;
    }
    List<String> values;
    try {
      values = redis.opsForValue().multiGet(ids.stream().map(RedisStockCache::key).toList());
    } catch (DataAccessException e) {
      failed("read", e);
      return found;
    }
    for (var i = 0; i < ids.size(); i++) {
      var value = values == null ? null : values.get(i);
      var entry = value == null ? null : read(ids.get(i), value);
      if (entry != null && entry.isFreshAt(now)) {
        found.put(ids.get(i), entry);
      }
    }
    hits.increment(found.size());
    misses.increment(ids.size() - found.size());
    return found;
  }

  @Override
  public void put(Collection<CachedStock> entries, Instant now) {
    var keys = new ArrayList<String>();
    var args = new ArrayList<String>();
    for (var entry : entries) {
      var timeToLive = Duration.between(now, entry.freshUntil()).toMillis();
      if (timeToLive > 0) {
        keys.add(key(entry.variantId()));
        args.add(json.writeValueAsString(Stored.of(entry)));
        args.add(String.valueOf(timeToLive));
      }
    }
    if (keys.isEmpty()) {
      return;
    }
    try {
      redis.execute(PUT, keys, args.toArray());
    } catch (DataAccessException e) {
      failed("write", e);
    }
  }

  /** The entry, or null, a miss, when it can't be read, so Postgres answers and writes it anew. */
  private CachedStock read(String variantId, String value) {
    try {
      return json.readValue(value, Stored.class).toEntry();
    } catch (JacksonException e) {
      log.warn("Couldn't read the Stock cache's entry of {}: {}", variantId, e.getMessage());
      return null;
    }
  }

  private void failed(String what, DataAccessException e) {
    errors.increment();
    log.warn(
        "Couldn't {} the Stock cache; Postgres serves Stock instead: {}",
        what,
        e.getMostSpecificCause().toString());
  }

  private static String key(String variantId) {
    return KEY_PREFIX + variantId;
  }
}
