package com.contractaudit.document.processing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Структурный chunking договоров. См. docs/retrieval-design.md §2 (chunking — не «порезать
 * на N символов»): режем ПО ПУНКТАМ. Новый пункт начинается там, где строка стартует с номера
 * («7.2», «8.1», «1.») — даже без пустой строки между пунктами; пустая строка тоже разделяет.
 * Соседние пункты не склеиваются, поэтому каждый виден отдельно. Каждому чанку проставляется
 * {@code clauseRef} — номер пункта в его начале (для ссылок «п. 7.2»). Слишком длинный пункт
 * режется по границам слов.
 */
@Component
public class ContractChunker {

    /** Номер пункта в начале строки: «7.2», «7.2.1» (с точками) либо «1.»/«2)» (с пунктуацией). */
    private static final Pattern CLAUSE_START = Pattern.compile(
            "(?:(?:п|пункт|статья|раздел|section|article|clause)\\.?\\s+)?"
                    + "(?:\\d{1,2}(?:\\.\\d{1,3})+|\\d{1,2}[.)])(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);

    /** Извлечение номера пункта из начала фрагмента (для clauseRef) — мягче, допускает «7.1». */
    private static final Pattern CLAUSE_REF = Pattern.compile(
            "^\\s*(?:п\\.?\\s*|статья\\s+|раздел\\s+|section\\s+|article\\s+)?(\\d{1,2}(?:\\.\\d{1,3}){0,3})[.)]?(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);

    private final int maxChars;

    public ContractChunker(ProcessingProperties properties) {
        this.maxChars = properties.chunkMaxChars();
    }

    public List<TextChunk> chunk(String text) {
        List<TextChunk> chunks = new ArrayList<>();
        int index = 0;
        for (String segment : splitSegments(text)) {
            String clauseRef = detectClauseRef(segment);
            if (segment.length() <= maxChars) {
                chunks.add(new TextChunk(index++, segment, clauseRef));
            } else {
                for (String piece : hardSplit(segment)) {
                    chunks.add(new TextChunk(index++, piece.strip(), clauseRef));
                }
            }
        }
        return chunks;
    }

    /** Делит текст на пункты: граница — пустая строка ИЛИ строка, начинающаяся с номера пункта. */
    private static List<String> splitSegments(String text) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : text.strip().split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                flush(current, segments);
                continue;
            }
            if (current.length() > 0 && CLAUSE_START.matcher(line).lookingAt()) {
                flush(current, segments);   // новый пункт — закрываем предыдущий
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        flush(current, segments);
        return segments;
    }

    private static void flush(StringBuilder buffer, List<String> segments) {
        String segment = buffer.toString().strip();
        if (!segment.isEmpty()) {
            segments.add(segment);
        }
        buffer.setLength(0);
    }

    private static String detectClauseRef(String segment) {
        Matcher matcher = CLAUSE_REF.matcher(segment);
        return matcher.lookingAt() ? matcher.group(1) : null;
    }

    /** Жёсткая нарезка слишком длинного пункта по границам слов в окна до {@code maxChars}. */
    private List<String> hardSplit(String segment) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < segment.length()) {
            int end = Math.min(start + maxChars, segment.length());
            if (end < segment.length()) {
                int lastSpace = segment.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            pieces.add(segment.substring(start, end));
            start = end == start ? end + maxChars : end;   // защита от зацикливания
        }
        return pieces;
    }
}
