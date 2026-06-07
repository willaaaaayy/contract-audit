package com.contractaudit.auth;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Строит {@link RSAKey} для подписи/проверки JWT: из PEM-ключей конфигурации (прод) либо
 * генерирует в памяти (dev). См. {@link JwtKeysConfig}.
 */
public final class RsaKeyLoader {

    private RsaKeyLoader() {
    }

    /** Загружает ключ из PEM: приватный (PKCS8) + публичный (X.509). */
    public static RSAKey fromPem(Resource privateKeyPem, Resource publicKeyPem) {
        try (InputStream priv = privateKeyPem.getInputStream();
             InputStream pub = publicKeyPem.getInputStream()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(priv);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(pub);
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать PEM-ключи JWT", e);
        }
    }

    /** Генерирует новый ключ в памяти (теряется при рестарте — только для dev/тестов). */
    public static RSAKey generateInMemory() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сгенерировать RSA-ключ для JWT", e);
        }
    }
}
