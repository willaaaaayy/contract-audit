package com.contractaudit.compliance;

import com.contractaudit.chunk.ChunkMatch;
import com.contractaudit.chunk.DocumentChunkRepository;
import com.contractaudit.policy.PolicyForCheck;
import com.contractaudit.policy.PolicyRepository;
import com.contractaudit.risk.RiskSeverity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Compliance Checker: сверяет договор с политиками компании. См. docs/retrieval-design.md
 * (Compliance Checker).
 *
 * <p>Проверка policy-centric — по каждой политике ищем в договоре релевантные пункты:
 * <ul>
 *   <li>если релевантных пунктов нет, а политика обязательна → {@code MISSING_REQUIRED}
 *       («отсутствие пункта — тоже риск», тонкость из дизайн-дока: RAG слеп к тому, чего нет,
 *       поэтому ловим это явно через обязательность политики);</li>
 *   <li>иначе LLM решает, противоречит ли пункт политике → {@code CONTRADICTION} с цитатой.</li>
 * </ul>
 */
@Service
public class ComplianceCheckService {

    private final ChatModel chatModel;
    private final PolicyRepository policyRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ComplianceFindingRepository findingRepository;
    private final TransactionTemplate transactionTemplate;
    private final ComplianceProperties properties;
    private final BeanOutputConverter<ComplianceVerdict> converter =
            new BeanOutputConverter<>(ComplianceVerdict.class);

    public ComplianceCheckService(ChatModel chatModel,
                                  PolicyRepository policyRepository,
                                  DocumentChunkRepository chunkRepository,
                                  ComplianceFindingRepository findingRepository,
                                  PlatformTransactionManager transactionManager,
                                  ComplianceProperties properties) {
        this.chatModel = chatModel;
        this.policyRepository = policyRepository;
        this.chunkRepository = chunkRepository;
        this.findingRepository = findingRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
    }

    /**
     * Сверяет документ со всеми политиками арендатора и сохраняет findings (перезаписывая прежние).
     */
    public List<ComplianceFinding> check(UUID documentId) {
        List<PolicyForCheck> policies = policyRepository.findAllForCheck();
        if (policies.isEmpty()) {
            throw new IllegalStateException("У арендатора нет политик для сверки");
        }

        List<ComplianceFinding> findings = new ArrayList<>();
        for (PolicyForCheck policy : policies) {
            List<ChunkMatch> relevant = chunkRepository.searchSimilarInDocument(
                    policy.embedding(), documentId, properties.relevantTopK());

            boolean addressed = !relevant.isEmpty()
                    && relevant.get(0).distance() <= properties.missingThreshold();

            if (!addressed) {
                if (policy.mandatory()) {
                    findings.add(missingRequired(documentId, policy));
                }
                continue;   // необязательная и не упомянута — не finding
            }

            ComplianceVerdict verdict = judge(policy, relevant);
            if (verdict != null && verdict.contradicts()) {
                findings.add(contradiction(documentId, policy, verdict));
            }
        }
        return persist(documentId, findings);
    }

    public List<ComplianceFinding> findByDocument(UUID documentId) {
        return findingRepository.findByDocumentId(documentId);
    }

    private ComplianceVerdict judge(PolicyForCheck policy, List<ChunkMatch> relevant) {
        String excerpts = relevant.stream()
                .map(c -> (c.clauseRef() != null ? "[" + c.clauseRef() + "] " : "") + c.chunkText())
                .collect(Collectors.joining("\n"));

        String prompt = """
                Ты — комплаенс-ассистент по B2B-договорам. Дана политика компании и релевантные
                пункты договора. Определи, ПРОТИВОРЕЧИТ ли договор политике. Опирайся только на
                приведённый текст, ничего не выдумывай. Если противоречия нет — contradicts=false.

                %s

                ПОЛИТИКА «%s»:
                %s

                ПУНКТЫ ДОГОВОРА:
                %s
                """.formatted(converter.getFormat(), policy.title(), policy.text(), excerpts);

        ChatResponse response = chatModel.call(new Prompt(prompt));
        return converter.convert(response.getResult().getOutput().getText());
    }

    private static ComplianceFinding missingRequired(UUID documentId, PolicyForCheck policy) {
        return new ComplianceFinding(documentId, policy.id(), ComplianceFindingType.MISSING_REQUIRED,
                RiskSeverity.HIGH, null, null,
                "Обязательная политика «%s» не отражена в договоре".formatted(policy.title()));
    }

    private static ComplianceFinding contradiction(UUID documentId, PolicyForCheck policy,
                                                   ComplianceVerdict verdict) {
        return new ComplianceFinding(documentId, policy.id(), ComplianceFindingType.CONTRADICTION,
                RiskSeverity.fromOrDefault(verdict.severity(), RiskSeverity.MEDIUM),
                verdict.clauseRef(), verdict.quote(),
                verdict.explanation() != null ? verdict.explanation()
                        : "Договор противоречит политике «%s»".formatted(policy.title()));
    }

    private List<ComplianceFinding> persist(UUID documentId, List<ComplianceFinding> findings) {
        return transactionTemplate.execute(status -> {
            findingRepository.deleteByDocumentId(documentId);   // tenant-scoped через @TenantId
            return findingRepository.saveAll(findings);
        });
    }
}
