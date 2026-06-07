package com.contractaudit.profile;

import java.util.List;

/**
 * Генеративный профиль документа: тип договора и подобранный под него набор блоков. Это
 * «динамический инвентарь» адаптивного экрана — LLM сам решает, какие профильные блоки уместны
 * для ЭТОГО договора (для NDA — срок конфиденциальности/территория, для SLA — уровни сервиса,
 * для поставки — оплата/штрафы/пролонгация), и наполняет их пунктами чек-листа.
 *
 * @param contractType тип договора (поставка / аренда / NDA / SLA / услуги / иное)
 * @param blocks       сгенерированные блоки, каждый — самостоятельная карточка на экране аудита
 */
public record AuditProfile(String contractType, List<ProfileBlock> blocks) {

    /**
     * Сгенерированный блок-карточка.
     *
     * @param key     стабильный ключ блока (для дедупликации/верстки)
     * @param title   заголовок блока
     * @param summary краткая суть блока (1–2 фразы)
     * @param items   пункты чек-листа блока
     */
    public record ProfileBlock(String key, String title, String summary, List<ProfileItem> items) {
    }

    /**
     * Пункт профильного чек-листа.
     *
     * @param label     что проверяем (например, «Срок конфиденциальности»)
     * @param status    OK / MISSING / ATTENTION — единый язык со шкалой severity
     * @param note      пояснение/значение (например, «3 года с даты подписания»)
     * @param clauseRef ссылка на пункт договора, если есть (проверяемость вывода)
     */
    public record ProfileItem(String label, String status, String note, String clauseRef) {
    }
}
