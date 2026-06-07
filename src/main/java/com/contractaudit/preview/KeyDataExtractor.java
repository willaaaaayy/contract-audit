package com.contractaudit.preview;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Лёгкое извлечение ключевых данных из текста договора без LLM: даты и денежные суммы.
 * Используется для быстрого превью загруженного PDF (см. {@link PreviewController}).
 */
@Component
public class KeyDataExtractor {

    private static final int MAX_ITEMS = 100;

    // Даты: 12.03.2024 / 12/03/24 / 2024-03-12 / 12 марта 2024
    private static final Pattern DATE = Pattern.compile(
            "\\b\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}\\b"
                    + "|\\b\\d{4}-\\d{2}-\\d{2}\\b"
                    + "|\\b\\d{1,2}\\s+(?:янв|фев|мар|апр|ма[йя]|июн|июл|авг|сен|окт|ноя|дек)[а-я]*\\s+\\d{4}\\b",
            Pattern.CASE_INSENSITIVE);

    // Суммы: $1,000.00 / €500 / 1 000 000 руб / 250 000,50 ₽ / 100 USD
    private static final Pattern AMOUNT = Pattern.compile(
            "[$€]\\s?\\d{1,3}(?:[ .,]\\d{3})*(?:[.,]\\d{2})?"
                    + "|\\d{1,3}(?:[ .]\\d{3})*(?:[.,]\\d{2})?\\s?(?:руб\\.?|₽|rub|usd|eur|долл\\.?|евро)",
            Pattern.CASE_INSENSITIVE);

    public KeyData extract(String text) {
        return new KeyData(findAll(DATE, text), findAll(AMOUNT, text));
    }

    private static List<String> findAll(Pattern pattern, String text) {
        Set<String> found = new LinkedHashSet<>();   // уникальные, в порядке появления
        Matcher matcher = pattern.matcher(text);
        while (matcher.find() && found.size() < MAX_ITEMS) {
            found.add(matcher.group().strip());
        }
        return new ArrayList<>(found);
    }

    /** Ключевые данные, найденные в договоре. */
    public record KeyData(List<String> dates, List<String> amounts) {
    }
}
