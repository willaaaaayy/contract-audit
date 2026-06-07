package com.contractaudit.retrieval;

import com.contractaudit.chunk.ChunkMatch;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Проверяет интеграцию {@link BgeReranker} с TEI {@code /rerank} на замоканном HTTP:
 * кандидаты переупорядочиваются по score сервиса и обрезаются до topK.
 */
class BgeRerankerTest {

    @Test
    void reordersCandidatesByScoreAndTruncatesToTopK() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BgeReranker reranker = new BgeReranker(builder.baseUrl("http://reranker").build());

        List<ChunkMatch> candidates = List.of(match("A"), match("B"), match("C"));   // index 0,1,2

        // Сервис ставит C (index 2) первым, затем A (0), затем B (1).
        String responseJson = """
                [ {"index": 2, "score": 0.91}, {"index": 0, "score": 0.42}, {"index": 1, "score": 0.05} ]
                """;
        server.expect(requestTo("http://reranker/rerank"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.query").value("кто платит за доставку"))
                .andExpect(jsonPath("$.texts.length()").value(3))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        List<ChunkMatch> result = reranker.rerank("кто платит за доставку", candidates, 2);

        assertThat(result).extracting(ChunkMatch::chunkText).containsExactly("C", "A");
        server.verify();
    }

    @Test
    void emptyCandidatesShortCircuitWithoutHttpCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BgeReranker reranker = new BgeReranker(builder.baseUrl("http://reranker").build());

        assertThat(reranker.rerank("q", List.of(), 5)).isEmpty();
        server.verify();   // ни одного запроса не ожидалось
    }

    private static ChunkMatch match(String text) {
        return new ChunkMatch(UUID.randomUUID(), UUID.randomUUID(), text, null, 0.0);
    }
}
