package com.contractaudit.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Реестр арендаторов. НЕ tenant-scoped (управление арендаторами — задача системного уровня). */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);
}
