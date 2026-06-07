package com.contractaudit.policy;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Управление политиками компании. При создании текст политики эмбеддится той же моделью,
 * что и чанки договоров, — чтобы их векторы были сопоставимы при сверке.
 */
@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final EmbeddingModel embeddingModel;

    public PolicyService(PolicyRepository policyRepository, EmbeddingModel embeddingModel) {
        this.policyRepository = policyRepository;
        this.embeddingModel = embeddingModel;
    }

    public UUID create(String title, String text, boolean mandatory) {
        float[] embedding = embeddingModel.embed(text);
        return policyRepository.save(new NewPolicy(title, text, mandatory, embedding));
    }

    public List<PolicyRepository.PolicySummary> list() {
        return policyRepository.list();
    }
}
