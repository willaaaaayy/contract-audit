package com.contractaudit.negotiation;

import com.contractaudit.risk.DocumentRisk;
import com.contractaudit.risk.DocumentRiskRepository;
import com.contractaudit.risk.RiskSeverity;
import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Negotiation Assistant со стаб-ChatModel: проверяет разбор предложений, обогащение severity/типа
 * из БД-риска (а не из LLM) и изоляцию арендатора — без обращения к реальной модели.
 */
@Import(NegotiationServiceTest.SuggestionChatConfig.class)
class NegotiationServiceTest extends AbstractPgvectorTest {

    @Autowired
    private NegotiationService negotiationService;

    @Autowired
    private DocumentRiskRepository riskRepository;

    @Test
    void suggest_rewritesRiskyClause_enrichesSeverityFromDb_andIsolates() {
        UUID tenant = createTenant("acme");
        UUID documentId = createDocument(tenant, "contract.pdf");
        withTenant(tenant, () -> riskRepository.save(new DocumentRisk(documentId, "штрафная санкция",
                RiskSeverity.HIGH, "7.2",
                "The Buyer pays for delivery within five business days", "Жёсткий срок оплаты")));

        List<ClauseSuggestion> suggestions =
                withTenant(tenant, () -> negotiationService.suggest(documentId, false));

        assertThat(suggestions).hasSize(1);
        ClauseSuggestion s = suggestions.get(0);
        assertThat(s.clauseRef()).isEqualTo("7.2");
        assertThat(s.severity()).isEqualTo(RiskSeverity.HIGH);          // авторитетно из БД
        assertThat(s.riskType()).isEqualTo("штрафная санкция");
        assertThat(s.suggested()).contains("ten business days");

        // Изоляция: другой арендатор не видит рисков → нечего предлагать (ошибка «нет рисков»).
        UUID other = createTenant("other");
        assertThatThrownBy(() -> withTenant(other, () -> negotiationService.suggest(documentId, false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suggest_failsWhenNoRisks() {
        UUID tenant = createTenant("empty");
        UUID documentId = createDocument(tenant, "no-risks.pdf");

        assertThatThrownBy(() -> withTenant(tenant, () -> negotiationService.suggest(documentId, false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @TestConfiguration
    static class SuggestionChatConfig {
        @Bean
        @Primary
        ChatModel stubChatModel() {
            return new StubChatModel("""
                    {
                      "suggestions": [
                        {
                          "clauseRef": "7.2",
                          "original": "The Buyer pays for delivery within five business days",
                          "suggested": "The Buyer pays for delivery within ten business days, late payment penalty capped at 0.05% per day",
                          "rationale": "Смягчили срок и ограничили неустойку"
                        }
                      ]
                    }
                    """);
        }
    }
}
