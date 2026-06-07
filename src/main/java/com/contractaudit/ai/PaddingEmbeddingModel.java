package com.contractaudit.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.Arrays;
import java.util.List;

/**
 * Приводит выход делегата-эмбеддера к фиксированной размерности {@code targetDimensions},
 * дополняя вектор нулями (или усекая, если он длиннее). Нужен, чтобы локальная модель с иной
 * размерностью (например, bge-m3 → 1024) ложилась в существующую схему {@code VECTOR(1536)}
 * и HNSW-индекс без миграций.
 *
 * <p><b>Почему это корректно.</b> Поиск идёт по косинусному расстоянию. Дополнение вектора
 * нулями не меняет ни скалярное произведение {@code a·b}, ни нормы {@code |a|},{@code |b|}
 * (добавленные координаты — нули у обоих векторов), значит косинус между любыми двумя
 * эмбеддингами сохраняется в точности. Ранжирование и recall не страдают; цена — лишние
 * нулевые координаты в индексе.
 *
 * <p>Важно: вектор запроса и векторы чанков должны прогоняться через один и тот же эмбеддер,
 * поэтому обёртка ставится как {@code @Primary} и используется всем пайплайном и поиском.
 */
public class PaddingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final int targetDimensions;

    public PaddingEmbeddingModel(EmbeddingModel delegate, int targetDimensions) {
        if (targetDimensions <= 0) {
            throw new IllegalArgumentException("targetDimensions должно быть > 0");
        }
        this.delegate = delegate;
        this.targetDimensions = targetDimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        EmbeddingResponse response = delegate.call(request);
        List<Embedding> padded = response.getResults().stream()
                .map(e -> new Embedding(pad(e.getOutput()), e.getIndex()))
                .toList();
        return new EmbeddingResponse(padded, response.getMetadata());
    }

    @Override
    public float[] embed(Document document) {
        return pad(delegate.embed(document));
    }

    @Override
    public int dimensions() {
        return targetDimensions;
    }

    /** Дополняет нулями до целевой длины; усекает, если делегат вернул больше (Matryoshka и т.п.). */
    private float[] pad(float[] vector) {
        if (vector.length == targetDimensions) {
            return vector;
        }
        if (vector.length > targetDimensions) {
            return Arrays.copyOf(vector, targetDimensions);
        }
        float[] out = new float[targetDimensions];
        System.arraycopy(vector, 0, out, 0, vector.length);
        return out;
    }
}
