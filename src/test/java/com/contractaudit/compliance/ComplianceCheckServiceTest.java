package com.contractaudit.compliance;

import com.contractaudit.chunk.NewChunk;
import com.contractaudit.policy.PolicyService;
import com.contractaudit.risk.RiskSeverity;
import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.StubChatModel;
import com.contractaudit.support.StubEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compliance Checker со стабами embed/chat: обязательная политика о лимите ответственности
 * против пункта о «неограниченной ответственности» → CONTRADICTION; обязательная политика о
 * защите данных без релевантного пункта → MISSING_REQUIRED; необязательная без совпадения →
 * ничего. Плюс изоляция арендатора.
 *
 * <p>{@code missing-threshold=0.8} даёт запас: пункт с общими токенами политики попадает ниже
 * порога (релевантен), а политики без пересечения словаря — выше (нерелевантны).
 */
@Import({StubEmbeddingConfig.class, ComplianceCheckServiceTest.ChatStub.class})
@TestPropertySource(properties = "compliance.missing-threshold=0.8")
class ComplianceCheckServiceTest extends AbstractPgvectorTest {

    private static final String VERDICT_JSON = """
            {
              "contradicts": true,
              "severity": "HIGH",
              "clauseRef": "7.2",
              "quote": "unlimited liability for all damages",
              "explanation": "Договор устанавливает неограниченную ответственность вопреки политике о лимите"
            }
            """;

    @TestConfiguration
    static class ChatStub {
        @Bean
        @Primary
        ChatModel stubChatModel() {
            return new StubChatModel(VERDICT_JSON);
        }
    }

    @Autowired
    private PolicyService policyService;
    @Autowired
    private ComplianceCheckService complianceService;
    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void check_flagsContradictionAndMissingMandatory_isolatedPerTenant() {
        UUID tenant = createTenant("acme");
        UUID documentId = createDocument(tenant, "contract.pdf");

        withTenant(tenant, () -> chunkRepository.saveAll(List.of(
                chunk(documentId, 0, "7.2 The Supplier bears unlimited liability for all damages.", "7.2"),
                chunk(documentId, 1, "8.1 Either party may terminate this agreement with notice.", "8.1"))));

        UUID liabilityPolicy = withTenant(tenant, () -> policyService.create(
                "Liability cap",
                "Supplier liability must be limited and capped, not unlimited damages.", true));
        UUID dataPolicy = withTenant(tenant, () -> policyService.create(
                "Data protection",
                "The contract must include a GDPR data processing clause.", true));
        withTenant(tenant, () -> policyService.create(
                "Governing law", "Governing law should be specified.", false));   // необязательная

        List<ComplianceFinding> findings = withTenant(tenant, () -> complianceService.check(documentId));

        assertThat(findings).hasSize(2);

        ComplianceFinding contradiction = findings.stream()
                .filter(f -> f.getFindingType() == ComplianceFindingType.CONTRADICTION)
                .findFirst().orElseThrow();
        assertThat(contradiction.getPolicyId()).isEqualTo(liabilityPolicy);
        assertThat(contradiction.getSeverity()).isEqualTo(RiskSeverity.HIGH);
        assertThat(contradiction.getClauseRef()).isEqualTo("7.2");
        assertThat(contradiction.getQuote()).contains("unlimited");

        ComplianceFinding missing = findings.stream()
                .filter(f -> f.getFindingType() == ComplianceFindingType.MISSING_REQUIRED)
                .findFirst().orElseThrow();
        assertThat(missing.getPolicyId()).isEqualTo(dataPolicy);
        assertThat(missing.getSeverity()).isEqualTo(RiskSeverity.HIGH);

        // Сохранены и доступны владельцу.
        assertThat(withTenant(tenant, () -> complianceService.findByDocument(documentId))).hasSize(2);

        // Изоляция: другой арендатор не видит findings чужого документа.
        UUID other = createTenant("other");
        assertThat(withTenant(other, () -> complianceService.findByDocument(documentId))).isEmpty();
    }

    private NewChunk chunk(UUID documentId, int index, String text, String clauseRef) {
        return new NewChunk(documentId, index, text, clauseRef, embeddingModel.embed(text));
    }
}
