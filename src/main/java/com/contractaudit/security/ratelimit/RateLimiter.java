package com.contractaudit.security.ratelimit;

/**
 * Абстракция ограничителя частоты. Реализации: {@link InMemoryRateLimiter} (один инстанс,
 * по умолчанию) и {@link RedisRateLimiter} (общий лимит для нескольких инстансов).
 */
public interface RateLimiter {

    /**
     * @param key ключ ограничения (обычно IP клиента)
     * @return {@code true} — запрос разрешён, {@code false} — лимит исчерпан
     */
    boolean tryAcquire(String key);
}
