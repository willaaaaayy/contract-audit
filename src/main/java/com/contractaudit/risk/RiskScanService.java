package com.contractaudit.risk;

import com.contractaudit.chunk.DocumentChunkRepository;
import com.contractaudit.common.error.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Risk Scanner: извлекает из договора риски (штрафы, сроки, суммы, ответственность) через
 * LLM со structured output. См. docs/retrieval-design.md (Risk Scanner).
 *
 * <p>Каждый риск обязан нести цитату и ссылку на пункт — иначе юрист не доверится выводу.
 * Промпт явно требует опираться только на текст (борьба с галлюцинациями).
 */
@Service
public class RiskScanService {

    private final ChatModel chatModel;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentRiskRepository riskRepository;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final BeanOutputConverter<RiskScanResult> converter =
            new BeanOutputConverter<>(RiskScanResult.class);

    public RiskScanService(ChatModel chatModel,
                           DocumentChunkRepository chunkRepository,
                           DocumentRiskRepository riskRepository,
                           PlatformTransactionManager transactionManager,
                           MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.chunkRepository = chunkRepository;
        this.riskRepository = riskRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
    }

    /**
     * Анализирует документ и сохраняет найденные риски (перезаписывая прежние — скан идемпотентен).
     *
     * @return сохранённые риски
     */
    public List<DocumentRisk> scan(UUID documentId) {
        List<String> texts = chunkRepository.findChunkTextsByDocument(documentId);
        if (texts.isEmpty()) {
            throw new NotFoundException(
                    "У документа %s нет чанков — он не обработан или не найден".formatted(documentId));
        }

        RiskScanResult result = analyze(String.join("\n\n", texts));   // LLM-вызов вне транзакции
        List<DocumentRisk> risks = persist(documentId, result);
        meterRegistry.counter("contract_audit.risks.found").increment(risks.size());
        return risks;
    }

    private RiskScanResult analyze(String contractText) {
        String prompt = """
                Ты — юридический ассистент по аудиту B2B-договоров. Найди в договоре риски:
                штрафные санкции, сроки, суммы, односторонние права, ответственность,
                автоматическую пролонгацию и подобное. Для каждого риска приведи точную цитату
                из текста и ссылку на пункт. Опирайся ТОЛЬКО на текст договора, ничего не выдумывай.

                %s

                ДОГОВОР:
                %s
                """.formatted(converter.getFormat(), contractText);

        ChatResponse response = chatModel.call(new Prompt(prompt));
        return converter.convert(response.getResult().getOutput().getText());
    }

    private List<DocumentRisk> persist(UUID documentId, RiskScanResult result) {
        List<RiskScanResult.RiskItem> items = result == null || result.risks() == null
                ? List.of() : result.risks();
        return transactionTemplate.execute(status -> {
            riskRepository.deleteByDocumentId(documentId);   // tenant-scoped через @TenantId
            List<DocumentRisk> risks = items.stream()
                    .map(item -> new DocumentRisk(documentId, item.riskType(),
                            RiskSeverity.fromOrDefault(item.severity(), RiskSeverity.MEDIUM),
                            item.clauseRef(), item.quote(), item.explanation()))
                    .toList();
            return riskRepository.saveAll(risks);
        });
    }

    public List<DocumentRisk> findByDocument(UUID documentId) {
        return riskRepository.findByDocumentId(documentId);
    }
}
