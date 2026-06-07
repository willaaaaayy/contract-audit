package com.contractaudit.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Стаб {@link ChatModel} для тестов — возвращает заранее заданный ответ, не обращаясь к LLM.
 * Промпт игнорируется; этого достаточно, чтобы проверить разбор structured output, сохранение
 * и изоляцию, не завися от реальной модели.
 */
public class StubChatModel implements ChatModel {

    private final String cannedResponse;

    public StubChatModel(String cannedResponse) {
        this.cannedResponse = cannedResponse;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(cannedResponse))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }
}
