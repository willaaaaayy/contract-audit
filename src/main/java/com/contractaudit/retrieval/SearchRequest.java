package com.contractaudit.retrieval;

import jakarta.validation.constraints.NotBlank;

/** Запрос семантического поиска по контрактам арендатора. */
public record SearchRequest(@NotBlank String query) {
}
