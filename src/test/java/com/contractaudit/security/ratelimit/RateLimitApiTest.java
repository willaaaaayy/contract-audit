package com.contractaudit.security.ratelimit;

import com.contractaudit.support.AbstractPgvectorTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit на auth-эндпоинтах: после исчерпания ёмкости приходит 429 с Retry-After.
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

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String body) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
