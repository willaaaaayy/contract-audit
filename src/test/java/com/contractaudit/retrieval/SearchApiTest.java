package com.contractaudit.retrieval;

import com.contractaudit.auth.AuthService;
import com.contractaudit.chunk.NewChunk;
import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.StubEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-поверхность семантического поиска: релевантный чанк находится (стаб-эмбеддер детерминирован,
 * запрос и чанк с одинаковым текстом дают одинаковый вектор), валидация отклоняет пустой и
 * сверхдлинный запрос в едином конверте ошибок.
 */
@AutoConfigureMockMvc
@Import(StubEmbeddingConfig.class)
class SearchApiTest extends AbstractPgvectorTest {

    private static final String QUERY = "кто платит за доставку";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private EmbeddingModel embeddingModel;   // стаб (@Primary из StubEmbeddingConfig)

    @Test
    void search_returnsRelevantChunkOfOwnTenant() throws Exception {
        String slug = "acme-" + UUID.randomUUID();
        String token = authService.register("Acme", slug, "admin@acme.com", "password1");
        UUID tenantId = UUID.fromString(decode(token).getClaimAsString("tenant_id"));

        UUID documentId = createDocument(tenantId, "delivery.pdf");
        withTenant(tenantId, () -> chunkRepository.saveAll(List.of(
                new NewChunk(documentId, 0, QUERY, "5.1", embeddingModel.embed(QUERY)))));

        mockMvc.perform(post("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + QUERY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty())
                .andExpect(jsonPath("$[0].clauseRef").value("5.1"));
    }

    @Test
    void search_rejectsBlankQuery_withFieldDetails() throws Exception {
        String token = registerFreshTenant();

        mockMvc.perform(post("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.details[0].field").value("query"));
    }

    @Test
    void search_rejectsOverlongQuery() throws Exception {
        String token = registerFreshTenant();

        mockMvc.perform(post("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    private String registerFreshTenant() {
        return authService.register("Acme", "acme-" + UUID.randomUUID(), "admin@acme.com", "password1");
    }

    private Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }
}
