package com.contractaudit.support;

import com.contractaudit.chunk.DocumentChunkRepository;
import com.contractaudit.document.Document;
import com.contractaudit.document.DocumentRepository;
import com.contractaudit.tenant.Tenant;
import com.contractaudit.tenant.TenantContext;
import com.contractaudit.tenant.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * База интеграционных тестов на реальном pgvector. Flyway применяет миграции (схема + HNSW)
 * при старте контекста. См. docs/retrieval-design.md §4.
 *
 * <p>Два режима выбора БД:
 * <ul>
 *   <li><b>Testcontainers</b> (по умолчанию, для CI) — поднимает контейнер
 *       {@code pgvector/pgvector};</li>
 *   <li><b>внешняя БД</b> ({@code EXTERNAL_DB=true}) — использует уже запущенный Postgres
 *       (например, из {@code docker-compose.yml}). Нужно там, где бандленный в
 *       Testcontainers docker-java несовместим с версией Docker-демона.</li>
 * </ul>
 *
 * <p>{@code spring.ai.openai.api-key=test} позволяет создать бины Spring AI без обращения
 * к OpenAI — в тестах изоляции/recall мы их не вызываем.
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test",
        "processing.poller.enabled=false"   // обработку в тестах запускаем вручную
})
public abstract class AbstractPgvectorTest {

    private static final boolean USE_EXTERNAL_DB =
            Boolean.parseBoolean(System.getenv().getOrDefault("EXTERNAL_DB", "false"));

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (USE_EXTERNAL_DB) {
            POSTGRES = null;
        } else {
            POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_DB) {
            registry.add("spring.datasource.url",
                    () -> System.getenv().getOrDefault("TEST_DB_URL",
                            "jdbc:postgresql://localhost:5432/contract_audit"));
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("TEST_DB_USER", "contract_audit"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "contract_audit"));
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    @Autowired
    protected TenantRepository tenantRepository;
    @Autowired
    protected DocumentRepository documentRepository;
    @Autowired
    protected DocumentChunkRepository chunkRepository;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected UUID createTenant(String name) {
        return tenantRepository.save(new Tenant(name, name + "-" + UUID.randomUUID())).getId();
    }

    protected UUID createDocument(UUID tenantId, String filename) {
        return withTenant(tenantId, () -> documentRepository.save(new Document(filename, null)).getId());
    }

    /** Выполнить действие в контексте арендатора, гарантированно очистив контекст после. */
    protected <T> T withTenant(UUID tenantId, Supplier<T> action) {
        TenantContext.set(tenantId);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    protected void withTenant(UUID tenantId, Runnable action) {
        withTenant(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
