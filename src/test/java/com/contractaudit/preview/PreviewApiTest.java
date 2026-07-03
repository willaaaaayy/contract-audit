package com.contractaudit.preview;

import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.PdfFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Публичный эндпоинт превью: загрузка PDF без аутентификации возвращает текст, пункты и
 * ключевые данные (даты/суммы) — основа браузерной демо-страницы. Мусорный ввод отсекается
 * валидатором загрузки ещё до PDFBox/OCR.
 */
@AutoConfigureMockMvc
class PreviewApiTest extends AbstractPgvectorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadPdf_returnsExtractedTextClausesAndKeyData_withoutAuth() throws Exception {
        byte[] pdf = PdfFixtures.makePdf(List.of(
                "7.2 The Buyer pays 500 USD by 12.03.2024.",
                "8.1 Either party may terminate with notice."));

        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", pdf);

        mockMvc.perform(multipart("/api/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("contract.pdf"))
                .andExpect(jsonPath("$.clauses").isNotEmpty())
                .andExpect(jsonPath("$.fullText").value(org.hamcrest.Matchers.containsString("Buyer")))
                .andExpect(jsonPath("$.dates").value(org.hamcrest.Matchers.hasItem("12.03.2024")))
                .andExpect(jsonPath("$.amounts").value(org.hamcrest.Matchers.hasItem("500 USD")));
    }

    @Test
    void uploadEmptyFile_isRejectedWithInvalidUpload() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "contract.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/preview").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_upload"));
    }

    @Test
    void uploadNonPdf_isRejectedWithInvalidUpload() throws Exception {
        MockMultipartFile txt = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/preview").file(txt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_upload"));
    }
}
