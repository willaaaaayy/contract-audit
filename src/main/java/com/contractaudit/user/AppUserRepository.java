package com.contractaudit.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Все запросы автоматически ограничены текущим арендатором (Hibernate {@code @TenantId}),
 * поэтому {@code findByEmail} ищет только среди пользователей этого арендатора — то, что нужно.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);
}
