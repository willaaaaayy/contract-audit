package com.contractaudit.security.ratelimit;

import com.contractaudit.support.AbstractPgvectorTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit на auth-эндпоинтах и публичном превью: после исчерпания ёмкости приходит 429
 * с Retry-After. Бюджеты групп независимы — исчерпание auth не задевает preview и наоборот.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.auth.capacity=2",
        "rate-limit.auth.refill-per-minute=1"   // практически без пополнения за время теста
})
class RateLimitApiTest extends AbstractPgvectorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_isRateLimitedAfterCapacity() throws Exception {
        String body = """
                {"slug":"nonexistent","email":"x@y.com","password":"password1"}
                """;

        // Ёмкость 2: первые два запроса проходят до контроллера (там 401 — арендатора нет).
        mockMvc.perform(login(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(login(body)).andExpect(status().isUnauthorized());

        // Третий отсекается лимитером ещё до контроллера.
        mockMvc.perform(login(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void preview_isRateLimitedAfterCapacity() throws Exception {
        // Текст достаточной длины, чтобы извлечение не приняло PDF за скан и не ушло в OCR.
        byte[] pdf = com.contractaudit.support.PdfFixtures.makePdf(java.util.List.of(
                "7.2 The Buyer pays 500 USD for delivery by 12.03.2024 without exceptions.",
                "8.1 Either party may terminate this agreement upon thirty days notice."));
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "contract.pdf", "application/pdf", pdf);

        // Отдельный бюджет группы preview: та же ёмкость 2, независим от auth-группы.
        mockMvc.perform(multipart("/api/preview").file(file)).andExpect(status().isOk());
        mockMvc.perform(multipart("/api/preview").file(file)).andExpect(status().isOk());

        mockMvc.perform(multipart("/api/preview").file(file))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String body) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
