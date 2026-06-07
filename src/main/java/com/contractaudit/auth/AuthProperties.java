package com.contractaudit.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Параметры выдачи JWT.
 *
 * @param issuer         значение claim {@code iss}; ресурс-сервер проверяет его при валидации
 * @param accessTokenTtl срок жизни access-токена
 * @param privateKey     PEM приватного ключа (PKCS8) для подписи; если не задан — ключ
 *                       генерируется в памяти (только для dev)
 * @param publicKey      PEM публичного ключа (X.509) для проверки
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(String issuer, Duration accessTokenTtl,
                             Resource privateKey, Resource publicKey) {

    public AuthProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "contract-audit";
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            accessTokenTtl = Duration.ofHours(1);
        }
    }

    /** Заданы ли оба ключа из конфигурации (иначе — fallback на in-memory генерацию). */
    public boolean hasConfiguredKeys() {
        return privateKey != null && publicKey != null;
    }
}
