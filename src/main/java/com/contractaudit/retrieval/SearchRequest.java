package com.contractaudit.retrieval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Запрос семантического поиска по контрактам арендатора. */
public record SearchRequest(@NotBlank @Size(max = 2000) String query) {
}
