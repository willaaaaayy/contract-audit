package com.contractaudit.document;

import com.contractaudit.auth.AuthService;
import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.PdfFixtures;
import com.contractaudit.support.StubEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-поверхность REST документов: загрузка (202 + PENDING), список, 404 по чужому/несуществующему
 * id, единый конверт ошибок для кривого UUID, пустого файла и отсутствующей части запроса,
 * 401 без токена. Обработка тестируется отдельно (пайплайн-e2e), здесь — только HTTP-слой.
 */
@AutoConfigureMockMvc
@Import(StubEmbeddingConfig.class)
class DocumentApiTest extends AbstractPgvectorTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;

    @Test
    void upload_registersDocumentAndReturnsAccepted() throws Exception {
        String token = registerFreshTenant();
        // Текст достаточной длины, чтобы фоновая обработка не приняла PDF за скан (иначе OCR).
        byte[] pdf = PdfFixtures.makePdf(List.of(
                "7.2 The Buyer pays 500 USD for delivery by 12.03.2024 without exceptions.",
                "8.1 Either party may terminate this agreement upon thirty days notice."));
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", pdf);

        mockMvc.perform(multipart("/api/documents").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.filename").value("contract.pdf"));

        mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("contract.pdf"));
    }

    @Test
    void get_unknownDocument_isNotFound() throws Exception {
        String token = registerFreshTenant();

        mockMvc.perform(get("/api/documents/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_malformedUuid_isBadRequestWithErrorEnvelope() throws Exception {
        String token = registerFreshTenant();

        mockMvc.perform(get("/api/documents/not-a-uuid")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
    }

    @Test
    void upload_emptyFile_isRejectedWithInvalidUpload() throws Exception {
        String token = registerFreshTenant();
        MockMultipartFile empty = new MockMultipartFile("file", "contract.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/documents").file(empty)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_upload"));
    }

    @Test
    void upload_withoutFilePart_isRejectedWithMissingFile() throws Exception {
        String token = registerFreshTenant();

        mockMvc.perform(multipart("/api/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_file"));
    }

    @Test
    void list_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    }

    private String registerFreshTenant() {
        return authService.register("Acme", "acme-" + UUID.randomUUID(), "admin@acme.com", "password1");
    }
}
