package com.contractaudit.observability;

import com.contractaudit.auth.AuthService;
import com.contractaudit.chunk.NewChunk;
import com.contractaudit.risk.RiskScanService;
import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.StubChatConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observability: кастомная бизнес-метрика инкрементируется при работе и экспонируется на
 * {@code /actuator/prometheus}.
 */
@Import(StubChatConfig.class)
@AutoConfigureMockMvc
class MetricsTest extends AbstractPgvectorTest {

    @Autowired
    private RiskScanService riskScanService;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;

    @Test
    void riskScan_incrementsCounter_andIsExposedViaPrometheus() throws Exception {
        UUID tenant = createTenant("acme");
        UUID documentId = createDocument(tenant, "contract.pdf");
        withTenant(tenant, () -> chunkRepository.saveAll(List.of(
                new NewChunk(documentId, 0, "7.2 The Buyer pays for delivery in five days.", "7.2", unit()))));

        double before = meterRegistry.counter("contract_audit.risks.found").count();
        withTenant(tenant, () -> riskScanService.scan(documentId));   // RISK_JSON содержит 1 риск
        double after = meterRegistry.counter("contract_audit.risks.found").count();

        assertThat(after - before).isEqualTo(1.0);

        // Метрика экспонируется через actuator (эндпоинт защищён — нужен валидный токен).
        String token = authService.register("Acme", "metrics-" + UUID.randomUUID(), "a@acme.com", "password1");
        mockMvc.perform(get("/actuator/metrics/contract_audit.risks.found")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("COUNT")));
    }

    private static float[] unit() {
        float[] v = new float[1536];
        v[0] = 1.0f;
        return v;
    }
}
