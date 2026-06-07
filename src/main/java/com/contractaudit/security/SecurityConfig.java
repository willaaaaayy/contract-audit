package com.contractaudit.security;

import com.contractaudit.security.ratelimit.RateLimitFilter;
import com.contractaudit.security.ratelimit.RateLimitProperties;
import com.contractaudit.security.ratelimit.RateLimiter;
import com.contractaudit.tenant.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Конфигурация ресурс-сервера: каждый запрос несёт Bearer JWT, подпись проверяется локально
 * по нашему публичному ключу ({@link com.contractaudit.auth.JwtKeysConfig}).
 *
 * <p>Эндпоинты {@code /api/auth/**} открыты (там как раз получают токен). {@link TenantContextFilter}
 * ставится сразу после аутентификации, чтобы прочитать {@code tenant_id} из проверенного токена.
 * {@code @EnableMethodSecurity} включает {@code @PreAuthorize} (роль из claim {@code role}).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           TenantContextFilter tenantContextFilter,
                                           JwtAuthenticationConverter jwtAuthenticationConverter,
                                           RateLimiter rateLimiter,
                                           RateLimitProperties rateLimitProperties) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // демо-страница превью PDF (без аутентификации)
                .requestMatchers("/", "/index.html", "/favicon.ico", "/api/preview").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
            // rate limit для /api/auth/** — до аутентификации, отсекаем перебор раньше
            .addFilterBefore(new RateLimitFilter(rateLimiter, rateLimitProperties), BearerTokenAuthenticationFilter.class)
            // tenant вытаскиваем после того, как JWT провалидирован и лежит в SecurityContext
            .addFilterAfter(tenantContextFilter, BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
