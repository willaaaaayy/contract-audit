package com.contractaudit.security.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Распределённый ограничитель на Redis: до ёмкости — разрешено, дальше — отказ. Лимит общий
 * (один и тот же ключ Redis для всех инстансов). Требует Redis (docker compose); иначе пропуск.
 */
class RedisRateLimiterTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static boolean available;

    @BeforeAll
    static void setUp() {
        try {
            connectionFactory = new LettuceConnectionFactory("localhost", 6379);
            connectionFactory.afterPropertiesSet();
            redisTemplate = new StringRedisTemplate(connectionFactory);
            redisTemplate.afterPropertiesSet();
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) c ->
                    c.ping());   // проверка соединения
            available = true;
        } catch (RuntimeException notReachable) {
            available = false;
        }
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void allowsUpToCapacity_thenRejects() {
        assumeTrue(available, "Redis недоступен на localhost:6379 — пропускаю");
        RateLimitProperties properties = new RateLimitProperties(2, 1);   // ёмкость 2, ~0 пополнения
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, properties);
        String key = "test-" + UUID.randomUUID();

        assertThat(limiter.tryAcquire(key)).isTrue();
        assertThat(limiter.tryAcquire(key)).isTrue();
        assertThat(limiter.tryAcquire(key)).as("сверх ёмкости").isFalse();
    }
}
