package com.contractaudit.document.processing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContractChunkerTest {

    private final ContractChunker chunker = new ContractChunker(new ProcessingProperties(120, 64, 300, 10));

    @Test
    void detectsClauseNumbersAtParagraphStart() {
        // Абзацы длиннее половины лимита (120) — вместе не помещаются, дают два чанка.
        String text = """
                7.1 Поставщик несёт полную ответственность за качество поставляемого товара и упаковки.

                7.2 Заказчик обязан оплатить доставку товара в течение пяти рабочих дней с момента приёмки.
                """;

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).extracting(TextChunk::clauseRef).containsExactly("7.1", "7.2");
    }

    @Test
    void keepsSmallClausesTogetherWithoutSplitting() {
        // Два коротких абзаца одного раздела влезают в один чанк (120 символов).
        String text = """
                5.1 Короткий пункт.

                5.2 Тоже короткий.
                """;

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).clauseRef()).isEqualTo("5.1");
        assertThat(chunks.get(0).text()).contains("5.1").contains("5.2");
    }

    @Test
    void hardSplitsOversizedParagraphWithoutLoss() {
        String longBody = "слово ".repeat(60).strip();   // ~360 символов, > лимита 120
        List<TextChunk> chunks = chunker.chunk("9.9 " + longBody);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(120));
        assertThat(chunks.get(0).clauseRef()).isEqualTo("9.9");
    }

    @Test
    void returnsEmptyForBlankInput() {
        assertThat(chunker.chunk("   \n\n  ")).isEmpty();
    }
}
