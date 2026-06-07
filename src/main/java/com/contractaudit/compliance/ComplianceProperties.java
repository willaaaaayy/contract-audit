package com.contractaudit.compliance;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры Compliance Checker.
 *
 * @param relevantTopK     сколько ближайших пунктов договора брать на политику
 * @param missingThreshold косинусное расстояние, выше которого пункт считается нерелевантным
 *                         политике; если у обязательной политики нет пунктов ближе порога —
 *                         это MISSING_REQUIRED
 */
@ConfigurationProperties(prefix = "compliance")
public record ComplianceProperties(int relevantTopK, double missingThreshold) {

    public ComplianceProperties {
        if (relevantTopK <= 0) {
            relevantTopK = 3;
        }
        if (missingThreshold <= 0) {
            missingThreshold = 0.6;
        }
    }
}
