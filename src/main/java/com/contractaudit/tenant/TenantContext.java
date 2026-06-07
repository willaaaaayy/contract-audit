package com.contractaudit.tenant;

import java.util.UUID;

/**
 * Держатель идентификатора текущего арендатора на время обработки запроса.
 *
 * <p>Значение кладётся один раз на входе ({@link TenantContextFilter} из claim'а
 * подписанного JWT) и снимается в {@code finally}. Дальше его читает
 * {@link TenantIdentifierResolver}, и Hibernate сам ограничивает все запросы по
 * {@code tenant_id} (нативная мультиарендность Hibernate 6, см. сущности с
 * {@code @TenantId}).
 *
 * <p><b>Ловушка ThreadLocal:</b> пул потоков переиспользует потоки. Если не вызвать
 * {@link #clear()}, следующий запрос унаследует чужой {@code tenant_id} — это утечка
 * между арендаторами. Поэтому очистка в {@code finally} фильтра обязательна.
 *
 * @see docs/retrieval-design.md §1
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    /** Текущий арендатор или {@code null}, если запрос вне арендного скоупа (логин, actuator). */
    public static UUID getCurrentOrNull() {
        return CURRENT.get();
    }

    /** Текущий арендатор; бросает, если контекст не установлен. Использовать в сервисах данных. */
    public static UUID require() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is not set for the current thread");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
