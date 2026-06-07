package com.contractaudit.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры ограничения частоты для auth-эндпоинтов (защита от перебора паролей).
 *
 * @param capacity        сколько запросов «в запасе» (всплеск)
 * @param refillPerMinute сколько запросов восстанавливается в минуту (установившаяся частота)
 */
@ConfigurationProperties(prefix = "rate-limit.auth")
public record RateLimitProperties(int capacity, int refillPerMinute) {

    public RateLimitProperties {
        if (capacity <= 0) {
            capacity = 10;
        }
        if (refillPerMinute <= 0) {
            refillPerMinute = 10;
        }
    }

    public double refillPerSecond() {
        return refillPerMinute / 60.0;
    }
}
