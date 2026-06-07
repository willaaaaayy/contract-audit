package com.contractaudit.chunk;

import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.VectorFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Leak-тест мультиарендности (docs/retrieval-design.md §4a): zero-tolerance к утечке
 * чанков чужого арендатора в семантическом поиске.
 *
 * <p>Усилен honeypot'ом: у арендатора B заводится чанк с вектором, В ТОЧНОСТИ равным
 * запросу арендатора A (косинусное расстояние 0 — ближайший возможный результат). Если
 * фильтрация по {@code tenant_id} где-то отвалится, именно он всплывёт первым. Обычный
 * random-датасет такую дыру может не вскрыть — чужие векторы просто далеко.
 */
class DocumentChunkIsolationTest extends AbstractPgvectorTest {

    @Test
    void searchIsIsolatedPerTenant_evenAgainstAnIdenticalHoneypot() {
        Random rnd = new Random(42);

        UUID tenantA = createTenant("tenant-A");
        UUID tenantB = createTenant("tenant-B");
        UUID docA = createDocument(tenantA, "contract-a.pdf");
        UUID docB = createDocument(tenantB, "contract-b.pdf");

        float[] query = VectorFixtures.randomUnitVector(rnd);

        // A: пара чанков, умеренно похожих на запрос и просто случайных.
        withTenant(tenantA, () -> chunkRepository.saveAll(List.of(
                new NewChunk(docA, 0, "A: пункт об оплате доставки", "п.4.1",
                        VectorFixtures.perturb(query, rnd, 0.6)),
                new NewChunk(docA, 1, "A: прочие условия", "п.9",
                        VectorFixtures.randomUnitVector(rnd)))));

        // B: HONEYPOT — вектор идентичен запросу A. Геометрически это абсолютный топ.
        withTenant(tenantB, () -> chunkRepository.saveAll(List.of(
                new NewChunk(docB, 0, "B: honeypot, идентичен запросу A", "п.1",
                        query.clone()))));

        // Поиск как A: honeypot арендатора B обязан отсутствовать в выдаче.
        List<ChunkMatch> resultsForA = withTenant(tenantA, () -> chunkRepository.searchSimilar(query, 10));

        assertThat(resultsForA).isNotEmpty();
        assertThat(resultsForA)
                .as("выдача A не должна содержать ни одного чанка из документа B")
                .allSatisfy(match -> assertThat(match.documentId()).isEqualTo(docA));
        assertThat(resultsForA)
                .as("honeypot арендатора B не должен просочиться, несмотря на нулевое расстояние")
                .noneMatch(match -> match.documentId().equals(docB));

        // И обратное: тот же honeypot ВИДЕН арендатору B — значит данные на месте,
        // и дело именно в изоляции, а не в том, что чанк «потерялся».
        List<ChunkMatch> resultsForB = withTenant(tenantB, () -> chunkRepository.searchSimilar(query, 10));

        assertThat(resultsForB).isNotEmpty();
        assertThat(resultsForB.get(0).documentId()).isEqualTo(docB);
        assertThat(resultsForB.get(0).distance())
                .as("идентичный вектор → косинусное расстояние ~0")
                .isCloseTo(0.0, within(1e-4));
    }
}
