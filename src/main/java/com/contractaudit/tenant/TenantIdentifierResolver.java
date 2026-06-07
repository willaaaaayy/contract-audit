package com.contractaudit.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Сообщает Hibernate, к какому арендатору относится текущая сессия.
 *
 * <p>Hibernate вызывает {@link #resolveCurrentTenantIdentifier()} при открытии сессии
 * и на основе результата:
 * <ul>
 *   <li>добавляет {@code AND tenant_id = ?} ко всем SELECT'ам по сущностям с {@code @TenantId};</li>
 *   <li>проставляет {@code tenant_id} при INSERT (значение из поля игнорируется в пользу резолвера).</li>
 * </ul>
 *
 * <p>Резолвер обязан вернуть non-null. Для запросов вне арендного скоупа (где к арендным
 * сущностям обращений быть не должно) возвращаем сентинел {@link #NO_TENANT} — он не
 * совпадёт ни с одним реальным {@code tenant_id}, поэтому случайная выборка вернёт пусто,
 * а не чужие данные (fail-closed).
 *
 * <p>Реализован как {@link HibernatePropertiesCustomizer}, чтобы зарегистрировать самого
 * себя в свойствах SessionFactory без отдельной конфигурации.
 *
 * @see docs/retrieval-design.md §1
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    /** Сентинел «нет арендатора»: не равен ни одному реальному tenant_id. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContext.getCurrentOrNull();
        return tenantId != null ? tenantId : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
