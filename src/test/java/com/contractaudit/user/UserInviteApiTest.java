package com.contractaudit.user;

import com.contractaudit.auth.AuthService;
import com.contractaudit.support.AbstractPgvectorTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invite-флоу сквозь весь auth-чейн (MockMvc): ADMIN приглашает пользователя, тот логинится в
 * том же арендаторе; не-ADMIN получает 403 на приглашение.
 */
@AutoConfigureMockMvc
class UserInviteApiTest extends AbstractPgvectorTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void admin_invitesAnalyst_whoCanLoginInSameTenant_butAnalystCannotInvite() throws Exception {
        String slug = "acme-" + UUID.randomUUID();
        String adminToken = authService.register("Acme", slug, "admin@acme.com", "password1");

        // ADMIN приглашает аналитика → 201.
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"analyst@acme.com","password":"password1","role":"ANALYST"}
                                """))
                .andExpect(status().isCreated());

        // Приглашённый логинится в том же арендаторе, с ролью ANALYST.
        String analystToken = authService.login(slug, "analyst@acme.com", "password1");
        Jwt analystJwt = jwtDecoder.decode(analystToken);
        Jwt adminJwt = jwtDecoder.decode(adminToken);
        assertThat(analystJwt.getClaimAsString("tenant_id")).isEqualTo(adminJwt.getClaimAsString("tenant_id"));
        assertThat(analystJwt.getClaimAsString("role")).isEqualTo("ANALYST");

        // Аналитик пригласить НЕ может → 403.
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"intern@acme.com","password":"password1","role":"ANALYST"}
                                """))
                .andExpect(status().isForbidden());
    }
}
