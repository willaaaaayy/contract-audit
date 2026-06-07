package com.contractaudit.auth;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Выпускает подписанные (RS256) access-токены. Claims несут {@code sub} (id пользователя),
 * {@code tenant_id} (для изоляции — его читает {@code TenantContextFilter}) и {@code role}.
 */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    public TokenService(JwtEncoder jwtEncoder, AuthProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * @param subject  id пользователя ({@code sub})
     * @param tenantId арендатор ({@code tenant_id}); передаётся явно, т.к. у только что
     *                 сохранённой через {@code @TenantId} сущности поле в памяти ещё null
     * @param role     роль пользователя
     */
    public String issue(UUID subject, UUID tenantId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .subject(subject.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("role", role)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
