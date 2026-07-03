package com.contractaudit.profile;

import com.contractaudit.chunk.DocumentChunkRepository;
import com.contractaudit.common.error.NotFoundException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Строит генеративный профиль документа: классифицирует тип договора и собирает специфичный
 * именно для него набор блоков-чек-листов (динамический инвентарь адаптивного экрана аудита).
 * Опирается только на текст договора (борьба с галлюцинациями); вывод — structured output.
 */
@Service
public class ProfileService {

    private final ChatModel chatModel;
    private final DocumentChunkRepository chunkRepository;
    private final BeanOutputConverter<AuditProfile> converter =
            new BeanOutputConverter<>(AuditProfile.class);

    public ProfileService(ChatModel chatModel, DocumentChunkRepository chunkRepository) {
        this.chatModel = chatModel;
        this.chunkRepository = chunkRepository;
    }

    public AuditProfile profile(UUID documentId) {
        List<String> texts = chunkRepository.findChunkTextsByDocument(documentId);
        if (texts.isEmpty()) {
            throw new NotFoundException(
                    "У документа %s нет чанков — он не обработан или не найден".formatted(documentId));
        }

        String prompt = """
                Ты — юридический ассистент. Определи тип договора (поставка, аренда, NDA, SLA,
                услуги или иное) и собери ПРОФИЛЬНЫЙ набор блоков именно для этого типа — только
                релевантные разделы (для NDA: срок конфиденциальности, территория, исключения,
                неустойка; для поставки: предмет, оплата, штрафы, пролонгация, гарантии; для SLA:
                уровни сервиса, доступность, компенсации и т.п.). Каждый блок — заголовок, краткая
                суть и пункты чек-листа со статусом OK / MISSING / ATTENTION, заметкой и ссылкой на
                пункт договора, если есть. Опирайся ТОЛЬКО на текст, ничего не выдумывай.

                %s

                ДОГОВОР:
                %s
                """.formatted(converter.getFormat(), String.join("\n\n", texts));

        ChatResponse response = chatModel.call(new Prompt(prompt));
        AuditProfile profile = converter.convert(response.getResult().getOutput().getText());
        if (profile == null) {
            return new AuditProfile("иное", List.of());
        }
        return profile;
    }
}
