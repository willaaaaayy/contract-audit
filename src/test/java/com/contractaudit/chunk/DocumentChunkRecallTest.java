package com.contractaudit.chunk;

import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.VectorFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * recall@k против точного перебора (docs/retrieval-design.md §4b): ловит over-filtering
 * на мелком арендаторе, чья доля в общей таблице мала.
 *
 * <p>Сцена: крупный «шумовой» арендатор (2000 чанков) создаёт давление over-filtering на
 * общий HNSW-индекс, мелкий арендатор (50 чанков) — это ~2.4% таблицы. Если бы не
 * {@code iterative_scan = relaxed_order}, фронт {@code ef_search} состоял бы в основном из
 * чужих векторов и recall мелкого арендатора просел бы. Тест проверяет, что выбранная
 * конфигурация это предотвращает.
 */
class DocumentChunkRecallTest extends AbstractPgvectorTest {

    private static final int K = 10;

    @Test
    void recallAtK_forSmallTenantUnderNoise_staysHigh() {
        Random rnd = new Random(7);

        UUID noiseTenant = createTenant("noise-big");
        UUID smallTenant = createTenant("small");
        UUID noiseDoc = createDocument(noiseTenant, "noise.pdf");
        UUID smallDoc = createDocument(smallTenant, "small.pdf");

        insertRandomChunks(noiseTenant, noiseDoc, 1000, rnd);
        insertRandomChunks(smallTenant, smallDoc, 50, rnd);

        float[] query = VectorFixtures.randomUnitVector(rnd);

        // Приближённый ответ HNSW (через репозиторий, с настройками из конфига).
        List<UUID> approx = withTenant(smallTenant, () -> chunkRepository.searchSimilar(query, K))
                .stream().map(ChunkMatch::id).toList();

        // Эталон: точный перебор (Seq Scan) в пределах того же арендатора.
        List<UUID> truth = exactTopK(smallTenant, query, K);

        double recall = recallAtK(truth, approx, K);
        assertThat(recall)
                .as("recall@%d мелкого арендатора под шумом крупного", K)
                .isGreaterThanOrEqualTo(0.9);
    }

    /** Эталонный точный KNN: enable_indexscan=off → Seq Scan, поэтому без HNSW-приближения. */
    private List<UUID> exactTopK(UUID tenantId, float[] query, int k) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbcTemplate.execute("SET LOCAL enable_indexscan = off");
            jdbcTemplate.execute("SET LOCAL enable_bitmapscan = off");
            return jdbcTemplate.query(
                    """
                    SELECT id FROM document_chunks
                    WHERE tenant_id = ?
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """,
                    (rs, n) -> rs.getObject("id", UUID.class),
                    tenantId, VectorFixtures.toPgVector(query), k);
        });
    }

    private void insertRandomChunks(UUID tenantId, UUID documentId, int count, Random rnd) {
        List<NewChunk> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(new NewChunk(documentId, i, "chunk " + i, null, VectorFixtures.randomUnitVector(rnd)));
        }
        withTenant(tenantId, () -> chunkRepository.saveAll(chunks));
    }

    private static double recallAtK(List<UUID> truth, List<UUID> approx, int k) {
        Set<UUID> truthSet = new HashSet<>(truth);
        long hits = approx.stream().limit(k).filter(truthSet::contains).count();
        return (double) hits / k;
    }
}
