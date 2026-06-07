package com.contractaudit.negotiation;

import com.contractaudit.risk.RiskSeverity;

/**
 * Предложение ИИ переписать рискованный пункт договора на более безопасную формулировку.
 * Несёт ссылку на пункт и исходный текст — правка проверяема (human-in-the-loop): юрист видит,
 * что было и что стало, и принимает или отклоняет.
 *
 * @param clauseRef ссылка на пункт (из исходного риска)
 * @param riskType  тип риска, который устраняет правка
 * @param severity  серьёзность исходного риска (авторитетно из БД, не из LLM)
 * @param original  исходная формулировка пункта
 * @param suggested предлагаемая формулировка
 * @param rationale почему так безопаснее
 */
public record ClauseSuggestion(String clauseRef, String riskType, RiskSeverity severity,
                               String original, String suggested, String rationale) {
}
