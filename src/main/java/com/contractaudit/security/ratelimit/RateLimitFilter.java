package com.contractaudit.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Ограничивает частоту запросов по IP: {@code /api/auth/**} (защита от brute-force при
 * входе/регистрации) и публичный {@code /api/preview} (синхронный PDF/OCR — без лимита это
 * вектор DoS). При превышении — {@code 429 Too Many Requests} с {@code Retry-After}.
 * Решение о допуске делегируется {@link RateLimiter} (in-memory либо общий на Redis).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Префикс URI → группа лимита; у каждой группы отдельный бюджет на IP. */
    private static final Map<String, String> LIMITED_PREFIXES =
            Map.of("/api/auth/", "auth", "/api/preview", "preview");

    private final RateLimiter rateLimiter;
    private final long retryAfterSeconds;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.retryAfterSeconds = Math.max(1, (long) Math.ceil(1.0 / properties.refillPerSecond()));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limitGroup(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (rateLimiter.tryAcquire(clientKey(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"too_many_requests\"}");
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()   // первый IP цепочки за прокси
                : request.getRemoteAddr();
        return ip + "|" + limitGroup(request);     // отдельный бюджет на группу эндпоинтов
    }

    private static String limitGroup(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return LIMITED_PREFIXES.entrySet().stream()
                .filter(e -> uri.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
