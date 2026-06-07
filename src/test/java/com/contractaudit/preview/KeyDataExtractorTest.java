package com.contractaudit.preview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyDataExtractorTest {

    private final KeyDataExtractor extractor = new KeyDataExtractor();

    @Test
    void extractsDatesAndAmounts() {
        String text = "Договор от 12.03.2024 на сумму 1 000 000 руб. Штраф $500 при просрочке. "
                + "Срок оплаты 2024-04-01.";

        KeyDataExtractor.KeyData data = extractor.extract(text);

        assertThat(data.dates()).contains("12.03.2024", "2024-04-01");
        assertThat(data.amounts()).anyMatch(a -> a.contains("1 000 000"));
        assertThat(data.amounts()).contains("$500");
    }

    @Test
    void returnsEmptyWhenNothingFound() {
        KeyDataExtractor.KeyData data = extractor.extract("просто текст без чисел и дат");
        assertThat(data.dates()).isEmpty();
        assertThat(data.amounts()).isEmpty();
    }
}
