package com.contractaudit.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Детерминированный стаб {@link EmbeddingModel} для тестов — без обращения к OpenAI.
 *
 * <p>Вектор строится хешированием токенов (bag-of-words): тексты с общими словами дают
 * близкие векторы (малое косинусное расстояние), с непересекающимся словарём —
 * ортогональные. Этого достаточно, чтобы семантический поиск в e2e-тесте находил пункт
 * по смыслу запроса, не завися от реальной модели.
 */
public class StubEmbeddingModel implements EmbeddingModel {

    public static final int DIM = 1536;

    @Override
    public float[] embed(String text) {
        return hashEmbed(text);
    }

    @Override
    public float[] embed(Document document) {
        return hashEmbed(document.getText());
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::hashEmbed).toList();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(hashEmbed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIM;
    }

    private float[] hashEmbed(String text) {
        float[] v = new float[DIM];
        for (String token : text.toLowerCase().split("[^\\p{L}\\p{Nd}]+")) {
            if (!token.isBlank()) {
                v[Math.floorMod(token.hashCode(), DIM)] += 1.0f;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1.0f;   // пустой текст → ненулевой вектор, чтобы не делить на ноль
            return v;
        }
        float inv = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < DIM; i++) {
            v[i] *= inv;
        }
        return v;
    }
}
