package com.contractaudit.support;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Подменяет реальный (OpenAI) {@link ChatModel} стабом с каноничным structured-output JSON.
 * {@code @Primary} — чтобы он внедрялся вместо боевого бина.
 */
@TestConfiguration
public class StubChatConfig {

    /** Каноничный ответ Risk Scanner'а: один риск с цитатой и ссылкой на пункт. */
    public static final String RISK_JSON = """
            {
              "risks": [
                {
                  "riskType": "штрафная санкция",
                  "severity": "HIGH",
                  "clauseRef": "7.2",
                  "quote": "The Buyer pays for delivery within five business days",
                  "explanation": "Жёсткий срок оплаты доставки под санкции"
                }
              ]
            }
            """;

    @Bean
    @Primary
    public ChatModel stubChatModel() {
        return new StubChatModel(RISK_JSON);
    }
}
