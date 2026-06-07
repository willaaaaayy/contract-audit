package com.contractaudit.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Приглашение пользователей в арендатора. Вызывается в контексте уже установленного
 * арендатора (его ставит {@code TenantContextFilter} из JWT), поэтому новый пользователь
 * автоматически создаётся в нужном арендаторе ({@code @TenantId}).
 */
@Service
public class UserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UUID invite(String email, String password, String role) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Пользователь %s уже существует".formatted(email));
        }
        AppUser user = userRepository.save(new AppUser(email, passwordEncoder.encode(password), role));
        return user.getId();
    }
}
