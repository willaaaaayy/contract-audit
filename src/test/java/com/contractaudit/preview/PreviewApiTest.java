package com.contractaudit.preview;

import com.contractaudit.support.AbstractPgvectorTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Публичный эндпоинт превью: загрузка PDF без аутентификации возвращает текст, пункты и
 * ключевые данные (даты/суммы) — основа браузерной демо-страницы.
 */
@AutoConfigureMockMvc
class PreviewApiTest extends AbstractPgvectorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadPdf_returnsExtractedTextClausesAndKeyData_withoutAuth() throws Exception {
        byte[] pdf = makePdf(List.of(
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

    private static byte[] makePdf(List<String> lines) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(20f);
                cs.newLineAtOffset(50, 760);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                    cs.newLine();
                }
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
