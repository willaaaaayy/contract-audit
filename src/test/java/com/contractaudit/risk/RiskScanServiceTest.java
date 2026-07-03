package com.contractaudit.risk;

import com.contractaudit.chunk.NewChunk;
import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.StubChatConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Risk Scanner со стаб-ChatModel: проверяет разбор structured output, сохранение рисков и
 * изоляцию арендатора — без обращения к OpenAI.
 */
@Import(StubChatConfig.class)
class RiskScanServiceTest extends AbstractPgvectorTest {

    @Autowired
    private RiskScanService riskScanService;

    @Test
    void scan_parsesStructuredOutput_persistsAndIsolates() {
        UUID tenant = createTenant("acme");
        UUID documentId = createDocument(tenant, "contract.pdf");

        withTenant(tenant, () -> chunkRepository.saveAll(List.of(
                new NewChunk(documentId, 0,
                        "7.2 The Buyer pays for delivery within five business days.", "7.2", unitVector()),
                new NewChunk(documentId, 1,
                        "8.1 Either party may terminate this agreement upon notice.", "8.1", unitVector()))));

        List<DocumentRisk> risks = withTenant(tenant, () -> riskScanService.scan(documentId));

        assertThat(risks).hasSize(1);
        DocumentRisk risk = risks.get(0);
        assertThat(risk.getSeverity()).isEqualTo(RiskSeverity.HIGH);
        assertThat(risk.getClauseRef()).isEqualTo("7.2");
        assertThat(risk.getQuote()).contains("delivery");
        assertThat(risk.getRiskType()).isEqualTo("штрафная санкция");

        // Сохранены и доступны владельцу.
        assertThat(withTenant(tenant, () -> riskScanService.findByDocument(documentId))).hasSize(1);

        // Изоляция: другой арендатор не видит рисков этого документа.
        UUID other = createTenant("other");
        assertThat(withTenant(other, () -> riskScanService.findByDocument(documentId))).isEmpty();
    }

    @Test
    void scan_failsWhenDocumentHasNoChunks() {
        UUID tenant = createTenant("empty-tenant");
        UUID documentId = createDocument(tenant, "unprocessed.pdf");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> withTenant(tenant, () -> riskScanService.scan(documentId)))
                .isInstanceOf(com.contractaudit.common.error.NotFoundException.class);
    }

    /** Любой ненулевой вектор: Risk Scanner читает только текст чанков, эмбеддинг тут не важен. */
    private static float[] unitVector() {
        float[] v = new float[1536];
        v[0] = 1.0f;
        return v;
    }
}
