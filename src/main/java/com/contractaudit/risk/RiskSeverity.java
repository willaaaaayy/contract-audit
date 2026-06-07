package com.contractaudit.risk;

import java.util.Locale;

/** Серьёзность найденного риска. */
public enum RiskSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** Терпимый разбор значения от LLM: неизвестное/пустое → {@code fallback}. */
    public static RiskSeverity fromOrDefault(String raw, RiskSeverity fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
