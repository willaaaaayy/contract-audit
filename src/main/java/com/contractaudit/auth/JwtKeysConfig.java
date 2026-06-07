package com.contractaudit.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPublicKey;

/**
 * Ключи и компоненты для подписи/проверки JWT. Приложение само и выпускает токены
 * ({@link JwtEncoder}), и проверяет их ({@link JwtDecoder}) — локально, по своему публичному
 * ключу, без обращения к внешнему IdP.
 *
 * <p>Ключ берётся из конфигурации ({@code auth.private-key}/{@code auth.public-key}, PEM —
 * подходит для секрет-менеджера). Если ключи не заданы, генерируется в памяти — удобно для
 * dev/тестов, но НЕ для прода (теряется при рестарте, не делится между инстансами).
 */
@Configuration
public class JwtKeysConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeysConfig.class);

    @Bean
    public RSAKey rsaKey(AuthProperties properties) {
        if (properties.hasConfiguredKeys()) {
            return RsaKeyLoader.fromPem(properties.privateKey(), properties.publicKey());
        }
        log.warn("RSA-ключ JWT не задан в конфигурации — генерирую в памяти (только для dev). "
                + "Для прода задайте auth.private-key/auth.public-key.");
        return RsaKeyLoader.generateInMemory();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey, AuthProperties properties) throws Exception {
        RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        // Проверяем подпись (по ключу) + срок и издателя (iss == наш issuer).
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
