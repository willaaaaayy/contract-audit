package com.contractaudit.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Извлекает {@code tenant_id} из claim'а уже провалидированного JWT и помещает его в
 * {@link TenantContext} на время запроса. Регистрируется в цепочке ПОСЛЕ аутентификации
 * (см. {@code SecurityConfig}), поэтому в {@link SecurityContextHolder} к этому моменту
 * лежит {@link Jwt} с проверенной подписью.
 *
 * <p>Источник арендатора — только подписанный токен. Никогда не из заголовка, параметра
 * или тела запроса: иначе клиент сможет выдать себя за чужого арендатора.
 *
 * @see docs/retrieval-design.md §1
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private final String tenantClaim;

    public TenantContextFilter(@Value("${security.jwt.tenant-claim:tenant_id}") String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            extractTenant().ifPresent(TenantContext::set);
            filterChain.doFilter(request, response);
        } finally {
            // ОБЯЗАТЕЛЬНО: иначе следующий запрос на этом потоке унаследует чужой tenant.
            TenantContext.clear();
        }
    }

    private java.util.Optional<UUID> extractTenant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String raw = jwt.getClaimAsString(tenantClaim);
            if (raw != null && !raw.isBlank()) {
                return java.util.Optional.of(UUID.fromString(raw));
            }
        }
        return java.util.Optional.empty();
    }
}
