package com.contractaudit.auth;

import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Выдача и валидация собственных JWT, плюс bootstrap логина через workspace-slug.
 */
class AuthServiceTest extends AbstractPgvectorTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private TenantRepository tenantRepositoryRef;

    @Test
    void register_thenLogin_issuesValidTokenWithTenantClaim() {
        String slug = "acme-" + UUID.randomUUID();
        authService.register("Acme Inc", slug, "admin@acme.com", "password1");

        String token = authService.login(slug, "admin@acme.com", "password1");

        Jwt jwt = jwtDecoder.decode(token);   // проверяет подпись, iss и срок
        UUID tenantId = tenantRepositoryRef.findBySlug(slug).orElseThrow().getId();

        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(tenantId.toString());
        assertThat(jwt.getSubject()).isNotBlank();
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("contract-audit");
    }

    @Test
    void login_withWrongPassword_isUnauthorized() {
        String slug = "globex-" + UUID.randomUUID();
        authService.register("Globex", slug, "admin@globex.com", "password1");

        assertThatThrownBy(() -> authService.login(slug, "admin@globex.com", "wrong-password"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void sameEmailInDifferentWorkspaces_resolvesToCorrectTenant() {
        // Один и тот же email допустим в разных workspace (email уникален в пределах арендатора).
        String slugA = "wsa-" + UUID.randomUUID();
        String slugB = "wsb-" + UUID.randomUUID();
        authService.register("Company A", slugA, "admin@shared.com", "password1");
        authService.register("Company B", slugB, "admin@shared.com", "password1");

        Jwt tokenA = jwtDecoder.decode(authService.login(slugA, "admin@shared.com", "password1"));
        Jwt tokenB = jwtDecoder.decode(authService.login(slugB, "admin@shared.com", "password1"));

        UUID tenantA = tenantRepositoryRef.findBySlug(slugA).orElseThrow().getId();
        UUID tenantB = tenantRepositoryRef.findBySlug(slugB).orElseThrow().getId();

        // Slug развёл одинаковые email по разным арендаторам.
        assertThat(tokenA.getClaimAsString("tenant_id")).isEqualTo(tenantA.toString());
        assertThat(tokenB.getClaimAsString("tenant_id")).isEqualTo(tenantB.toString());
        assertThat(tokenA.getClaimAsString("tenant_id")).isNotEqualTo(tokenB.getClaimAsString("tenant_id"));
    }

    @Test
    void register_withTakenSlug_isConflict() {
        String slug = "dup-" + UUID.randomUUID();
        authService.register("First", slug, "a@first.com", "password1");

        assertThatThrownBy(() -> authService.register("Second", slug, "b@second.com", "password1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }
}
