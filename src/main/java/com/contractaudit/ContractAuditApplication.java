package com.contractaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI-ассистент для анализа и аудита B2B-контрактов.
 *
 * <p>Архитектурные решения по слою хранения и поиска задокументированы в
 * {@code docs/retrieval-design.md}. Ключевой инвариант всего приложения:
 * ни один запрос к данным не выполняется без ограничения по {@code tenant_id}
 * (см. пакет {@code com.contractaudit.tenant}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ContractAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractAuditApplication.class, args);
    }
}
