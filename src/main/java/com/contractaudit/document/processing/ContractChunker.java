package com.contractaudit.document.processing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Структурный chunking договоров. См. docs/retrieval-design.md §2 (chunking — не «порезать
 * на N символов»): режем по абзацам/пунктам и НЕ разрываем пункт посередине, пока он влезает
 * в целевой размер. Каждому чанку проставляем {@code clauseRef} — номер пункта в его начале,
 * чтобы найденные риски могли ссылаться на «п. 7.2».
 */
@Component
public class ContractChunker {

    /** Номер пункта в начале абзаца: «7», «7.2», «7.2.1», возможно с префиксом «п.»/«Статья». */
    private static final Pattern CLAUSE = Pattern.compile(
            "^\\s*(?:п\\.?\\s*|статья\\s+|раздел\\s+|section\\s+|article\\s+)?(\\d+(?:\\.\\d+){0,3})[.)]?(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);

    /** Граница абзаца — одна или несколько пустых строк. */
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\R\\s*\\R");

    private final int maxChars;

    public ContractChunker(ProcessingProperties properties) {
        this.maxChars = properties.chunkMaxChars();
    }

    public List<TextChunk> chunk(String text) {
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String chunkClause = null;     // пункт в начале текущего собираемого чанка
        String currentClause = null;   // последний встреченный номер пункта
        int index = 0;

        for (String paragraph : splitParagraphs(text)) {
            String detected = detectClause(paragraph);
            if (detected != null) {
                currentClause = detected;
            }

            // Не влезает — закрываем текущий чанк.
            if (buffer.length() > 0 && buffer.length() + paragraph.length() + 1 > maxChars) {
                chunks.add(new TextChunk(index++, buffer.toString().strip(), chunkClause));
                buffer.setLength(0);
            }
            if (buffer.length() == 0) {
                chunkClause = currentClause;   // пункт фиксируем по началу чанка
            }

            // Абзац длиннее лимита — жёстко режем по границам слов (буфер уже сброшен выше).
            if (paragraph.length() > maxChars) {
                for (String piece : hardSplit(paragraph)) {
                    chunks.add(new TextChunk(index++, piece.strip(), currentClause));
                }
                continue;
            }

            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(paragraph);
        }

        if (buffer.length() > 0) {
            chunks.add(new TextChunk(index, buffer.toString().strip(), chunkClause));
        }
        return chunks;
    }

    private static List<String> splitParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        for (String part : PARAGRAPH_BREAK.split(text.strip())) {
            String normalized = part.strip();
            if (!normalized.isEmpty()) {
                paragraphs.add(normalized);
            }
        }
        return paragraphs;
    }

    private static String detectClause(String paragraph) {
        Matcher matcher = CLAUSE.matcher(paragraph);
        return matcher.lookingAt() ? matcher.group(1) : null;
    }

    /** Жёсткая нарезка слишком длинного абзаца по границам слов в окна до {@code maxChars}. */
    private List<String> hardSplit(String paragraph) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + maxChars, paragraph.length());
            if (end < paragraph.length()) {
                int lastSpace = paragraph.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            pieces.add(paragraph.substring(start, end));
            start = end == start ? end + maxChars : end;   // защита от зацикливания
        }
        return pieces;
    }
}
