package com.contractaudit.compliance;

/** Тип несоответствия договора политикам компании. */
public enum ComplianceFindingType {
    /** Пункт договора противоречит политике. */
    CONTRADICTION,
    /** Обязательная политика не отражена в договоре (отсутствие пункта — тоже риск). */
    MISSING_REQUIRED
}
