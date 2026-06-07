package com.contractaudit.security.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory ограничитель (token-bucket на инстанс). По умолчанию. Для нескольких реплик
 * лимит окажется «на инстанс» — тогда нужен {@link RedisRateLimiter} ({@code rate-limit.backend=redis}).
 */
@Component
@ConditionalOnProperty(prefix = "rate-limit", name = "backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private final RateLimitProperties properties;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(String key) {
        return buckets.computeIfAbsent(key,
                k -> new TokenBucket(properties.capacity(), properties.refillPerSecond())).tryConsume();
    }
}
