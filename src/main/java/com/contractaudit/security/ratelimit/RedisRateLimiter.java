package com.contractaudit.security.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Распределённый ограничитель частоты на Redis — общий лимит для всех инстансов приложения.
 * Token-bucket вычисляется атомарно в Redis через Lua-скрипт (чтение, пополнение по времени,
 * списание и запись — за одну операцию, без гонок между репликами).
 *
 * <p>Активируется {@code rate-limit.backend=redis}.
 */
@Component
@ConditionalOnProperty(prefix = "rate-limit", name = "backend", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    // KEYS[1]=ключ; ARGV: capacity, refillPerSec, nowMillis. Возвращает 1 (разрешено) или 0.
    private static final String LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refillPerSec = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then tokens = capacity; ts = now end
            local delta = math.max(0, now - ts) / 1000.0
            tokens = math.min(capacity, tokens + delta * refillPerSec)
            local allowed = 0
            if tokens >= 1 then tokens = tokens - 1; allowed = 1 end
            redis.call('HSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, math.ceil(capacity / refillPerSec * 1000) + 1000)
            return allowed
            """;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;
    private final RateLimitProperties properties;

    public RedisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    @Override
    public boolean tryAcquire(String key) {
        Long allowed = redisTemplate.execute(script, List.of("rate-limit:" + key),
                Integer.toString(properties.capacity()),
                Double.toString(properties.refillPerSecond()),
                Long.toString(System.currentTimeMillis()));
        return allowed != null && allowed == 1L;
    }
}
