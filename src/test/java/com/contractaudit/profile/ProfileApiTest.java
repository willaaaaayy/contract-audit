package com.contractaudit.profile;

import com.contractaudit.auth.AuthService;
import com.contractaudit.support.AbstractPgvectorTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-поверхность профиля документа: без токена — 401, по несуществующему/необработанному
 * документу — 404 в едином конверте ошибок (LLM при этом не вызывается).
 */
@AutoConfigureMockMvc
class ProfileApiTest extends AbstractPgvectorTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;

    @Test
    void profile_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/profile", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profile_forUnknownDocument_isNotFoundWithErrorEnvelope() throws Exception {
        String slug = "acme-" + UUID.randomUUID();
        String token = authService.register("Acme", slug, "admin@acme.com", "password1");

        mockMvc.perform(post("/api/documents/{id}/profile", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
}
