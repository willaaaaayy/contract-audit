package com.contractaudit.negotiation;

import com.contractaudit.risk.DocumentRisk;
import com.contractaudit.risk.DocumentRiskRepository;
import com.contractaudit.risk.RiskSeverity;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Negotiation Assistant: для найденных рисков предлагает безопасные контр-формулировки пунктов
 * (правка договора с предложениями ИИ). Работает поверх уже сохранённых рисков — берёт пункт,
 * его цитату и объяснение риска и просит LLM переписать формулировку так, чтобы снять риск,
 * сохранив юридический смысл.
 *
 * <p>Предложения не персистятся: это on-demand вывод, который юрист принимает или отклоняет
 * (human-in-the-loop). Серьёзность и тип берутся из БД-риска, а не из LLM — авторитетный источник.
 */
@Service
public class NegotiationService {

    private final ChatModel chatModel;
    private final DocumentRiskRepository riskRepository;
    private final MeterRegistry meterRegistry;
    private final BeanOutputConverter<SuggestionResult> converter =
            new BeanOutputConverter<>(SuggestionResult.class);

    public NegotiationService(ChatModel chatModel,
                              DocumentRiskRepository riskRepository,
                              MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.riskRepository = riskRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Генерирует предложения по правкам для рисков документа.
     *
     * @param documentId  документ (tenant-scoped через {@code @TenantId})
     * @param includeAll  true — по всем рискам; false — только HIGH/CRITICAL (если таких нет,
     *                    откатываемся ко всем, чтобы юрист всегда получил результат)
     */
    public List<ClauseSuggestion> suggest(UUID documentId, boolean includeAll) {
        List<DocumentRisk> risks = riskRepository.findByDocumentId(documentId);
        if (risks.isEmpty()) {
            throw new IllegalStateException(
                    "У документа %s нет рисков — сначала запустите Risk Scanner".formatted(documentId));
        }

        List<DocumentRisk> target = includeAll ? risks : risks.stream()
                .filter(r -> r.getSeverity() == RiskSeverity.HIGH || r.getSeverity() == RiskSeverity.CRITICAL)
                .toList();
        if (target.isEmpty()) {
            target = risks;
        }

        SuggestionResult result = generate(target);
        Map<String, DocumentRisk> byClause = risks.stream()
                .filter(r -> r.getClauseRef() != null)
                .collect(Collectors.toMap(DocumentRisk::getClauseRef, Function.identity(), (a, b) -> a));

        List<SuggestionResult.Item> items = result == null || result.suggestions() == null
                ? List.of() : result.suggestions();
        List<ClauseSuggestion> suggestions = items.stream()
                .map(item -> toSuggestion(item, byClause))
                .filter(s -> s.suggested() != null && !s.suggested().isBlank())
                .toList();

        meterRegistry.counter("contract_audit.suggestions.generated").increment(suggestions.size());
        return suggestions;
    }

    private ClauseSuggestion toSuggestion(SuggestionResult.Item item, Map<String, DocumentRisk> byClause) {
        DocumentRisk risk = item.clauseRef() == null ? null : byClause.get(item.clauseRef());
        String riskType = risk != null ? risk.getRiskType() : "риск";
        RiskSeverity severity = risk != null ? risk.getSeverity() : RiskSeverity.MEDIUM;
        String original = item.original() != null && !item.original().isBlank()
                ? item.original()
                : (risk != null ? risk.getQuote() : "");
        return new ClauseSuggestion(item.clauseRef(), riskType, severity, original,
                item.suggested(), item.rationale());
    }

    private SuggestionResult generate(List<DocumentRisk> risks) {
        String clauses = risks.stream()
                .map(r -> "Пункт %s (%s, %s): «%s». Риск: %s".formatted(
                        r.getClauseRef(), r.getRiskType(), r.getSeverity(), r.getQuote(), r.getExplanation()))
                .collect(Collectors.joining("\n"));

        String prompt = """
                Ты — юрист по договорной работе. Для каждого перечисленного рискованного пункта
                предложи более безопасную для нас формулировку: сними или смягчи риск, сохранив
                юридический смысл и применимость. Верни ссылку на пункт (clauseRef), исходную
                формулировку (original), предложенную (suggested) и краткое обоснование (rationale)
                на русском. Не выдумывай пунктов, которых нет в списке.

                %s

                РИСКОВАННЫЕ ПУНКТЫ:
                %s
                """.formatted(converter.getFormat(), clauses);

        ChatResponse response = chatModel.call(new Prompt(prompt));
        return converter.convert(response.getResult().getOutput().getText());
    }
}
