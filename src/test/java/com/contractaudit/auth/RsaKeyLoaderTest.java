package com.contractaudit.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Загрузка RSA-ключей из PEM и сквозной roundtrip подпись→проверка теми же ключами.
 */
class RsaKeyLoaderTest {

    @Test
    void loadsPemKeys_andSignsThenVerifies() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();

        ByteArrayResource privatePem = new ByteArrayResource(
                pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()).getBytes());
        ByteArrayResource publicPem = new ByteArrayResource(
                pem("PUBLIC KEY", keyPair.getPublic().getEncoded()).getBytes());

        RSAKey rsaKey = RsaKeyLoader.fromPem(privatePem, publicPem);
        assertThat(rsaKey.toRSAPublicKey()).isEqualTo(keyPair.getPublic());
        assertThat(rsaKey.isPrivate()).isTrue();

        // Подписываем загруженным ключом и проверяем его же публичной частью.
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("contract-audit")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("user-1")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("user-1");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("contract-audit");
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN %s-----%n%s%n-----END %s-----%n".formatted(type, body, type);
    }
}
