package com.contractaudit.profile;

import com.contractaudit.chunk.NewChunk;
import com.contractaudit.common.error.NotFoundException;
import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Генеративный профиль документа со стаб-ChatModel: разбор structured output, отказ по
 * необработанному документу и изоляция арендатора — без обращения к LLM.
 */
@Import(ProfileServiceTest.ProfileChatConfig.class)
class ProfileServiceTest extends AbstractPgvectorTest {

    @Autowired
    private ProfileService profileService;

    @Test
    void profile_parsesStructuredOutput() {
        UUID tenant = createTenant("acme");
        UUID documentId = createDocument(tenant, "nda.pdf");
        withTenant(tenant, () -> chunkRepository.saveAll(List.of(
                new NewChunk(documentId, 0,
                        "3.1 Confidential information shall be protected for 3 years.", "3.1", unitVector()))));

        AuditProfile profile = withTenant(tenant, () -> profileService.profile(documentId));

        assertThat(profile.contractType()).isEqualTo("NDA");
        assertThat(profile.blocks()).hasSize(1);
        AuditProfile.ProfileBlock block = profile.blocks().get(0);
        assertThat(block.key()).isEqualTo("confidentiality");
        assertThat(block.items()).hasSize(1);
        assertThat(block.items().get(0).status()).isEqualTo("OK");
        assertThat(block.items().get(0).clauseRef()).isEqualTo("3.1");
    }

    @Test
    void profile_failsWhenDocumentHasNoChunks() {
        UUID tenant = createTenant("empty-tenant");
        UUID documentId = createDocument(tenant, "unprocessed.pdf");

        assertThatThrownBy(() -> withTenant(tenant, () -> profileService.profile(documentId)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void profile_isIsolatedPerTenant() {
        UUID tenant = createTenant("owner");
        UUID documentId = createDocument(tenant, "nda.pdf");
        withTenant(tenant, () -> chunkRepository.saveAll(List.of(
                new NewChunk(documentId, 0, "3.1 Confidentiality.", "3.1", unitVector()))));

        // Чужой арендатор не видит чанков → для него документа «нет» (404, а не 403).
        UUID other = createTenant("other");
        assertThatThrownBy(() -> withTenant(other, () -> profileService.profile(documentId)))
                .isInstanceOf(NotFoundException.class);
    }

    private static float[] unitVector() {
        float[] v = new float[1536];
        v[0] = 1.0f;
        return v;
    }

    @TestConfiguration
    static class ProfileChatConfig {

        /** Каноничный ответ профилировщика: NDA с одним блоком конфиденциальности. */
        static final String PROFILE_JSON = """
                {
                  "contractType": "NDA",
                  "blocks": [
                    {
                      "key": "confidentiality",
                      "title": "Конфиденциальность",
                      "summary": "Срок защиты информации задан явно.",
                      "items": [
                        {
                          "label": "Срок конфиденциальности",
                          "status": "OK",
                          "note": "3 года с даты подписания",
                          "clauseRef": "3.1"
                        }
                      ]
                    }
                  ]
                }
                """;

        @Bean
        @Primary
        public ChatModel profileStubChatModel() {
            return new StubChatModel(PROFILE_JSON);
        }
    }
}
