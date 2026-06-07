package com.contractaudit.auth;

import com.contractaudit.tenant.Tenant;
import com.contractaudit.tenant.TenantContext;
import com.contractaudit.tenant.TenantRepository;
import com.contractaudit.user.AppUser;
import com.contractaudit.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Регистрация компании и вход пользователей. Выдаёт собственные JWT.
 *
 * <p><b>Bootstrap логина в мультиарендности:</b> {@code findByEmail} уже ограничен
 * {@code @TenantId}, но при входе арендатор ещё неизвестен. Поэтому сначала по {@code slug}
 * находим арендатора (глобально, через реестр {@link TenantRepository}), ставим его в
 * {@link TenantContext}, и дальнейший поиск пользователя естественно изолирован — то, что нужно.
 */
@Service
public class AuthService {

    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(TenantRepository tenantRepository,
                       AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /**
     * Регистрирует компанию (арендатора) и её первого пользователя-администратора.
     *
     * <p>Без {@code @Transactional} намеренно: Hibernate фиксирует арендатора при ОТКРЫТИИ
     * сессии. Нужно, чтобы сессия для вставки пользователя открылась уже ПОСЛЕ установки
     * {@link TenantContext}. Поэтому каждый вызов репозитория идёт своей транзакцией —
     * вставка tenant'а, затем (с контекстом) вставка пользователя в новой сессии.
     */
    public String register(String companyName, String slug, String email, String password) {
        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workspace '%s' уже занят".formatted(slug));
        }
        // saveAndFlush: tenant_id нужен резолверу @TenantId при сохранении пользователя.
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(companyName, slug));

        TenantContext.set(tenant.getId());
        try {
            // saveAndFlush: INSERT должен пройти, пока контекст установлен. Иначе flush
            // отложится до коммита register() — уже после clear() ниже — и @TenantId
            // резолвер подставит NO_TENANT.
            AppUser admin = userRepository.saveAndFlush(
                    new AppUser(email, passwordEncoder.encode(password), "ADMIN"));
            return tokenService.issue(admin.getId(), tenant.getId(), admin.getRole());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Вход: {@code slug} определяет арендатора, затем проверяются email и пароль в его контексте.
     * Без {@code @Transactional} — по той же причине, что и {@link #register}: поиск пользователя
     * должен идти в сессии, открытой уже с установленным {@link TenantContext}.
     */
    public String login(String slug, String email, String password) {
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow(this::invalidCredentials);

        TenantContext.set(tenant.getId());
        try {
            AppUser user = userRepository.findByEmail(email).orElseThrow(this::invalidCredentials);
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw invalidCredentials();
            }
            return tokenService.issue(user.getId(), user.getTenantId(), user.getRole());
        } finally {
            TenantContext.clear();
        }
    }

    /** Единый ответ на любую ошибку входа — не раскрываем, что именно не так. */
    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверные учётные данные");
    }
}
