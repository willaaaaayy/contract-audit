package com.contractaudit.support;

import java.util.Random;

/** Генерация синтетических эмбеддингов для тестов поиска (детерминированно по сиду). */
public final class VectorFixtures {

    /** Размерность совпадает с VECTOR(1536) и моделью text-embedding-3-small. */
    public static final int DIM = 1536;

    private VectorFixtures() {
    }

    /** Случайный единичный вектор (нормализован — корректно для косинусного расстояния). */
    public static float[] randomUnitVector(Random rnd) {
        float[] v = new float[DIM];
        for (int i = 0; i < DIM; i++) {
            v[i] = (float) rnd.nextGaussian();
        }
        return normalize(v);
    }

    /** Вектор рядом с {@code base}: тот же смысл с небольшим шумом. */
    public static float[] perturb(float[] base, Random rnd, double noise) {
        float[] v = base.clone();
        for (int i = 0; i < DIM; i++) {
            v[i] += (float) (rnd.nextGaussian() * noise);
        }
        return normalize(v);
    }

    private static float[] normalize(float[] v) {
        double sumSq = 0;
        for (float x : v) {
            sumSq += x * x;
        }
        float inv = (float) (1.0 / Math.sqrt(sumSq));
        for (int i = 0; i < v.length; i++) {
            v[i] *= inv;
        }
        return v;
    }

    /** float[] → pgvector-литерал {@code [..]} для bind как ::vector в тестовых запросах. */
    public static String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
