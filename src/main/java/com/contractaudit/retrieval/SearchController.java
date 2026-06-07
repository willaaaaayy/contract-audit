package com.contractaudit.retrieval;

import com.contractaudit.chunk.ChunkMatch;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Семантический поиск по смыслу (не по словам) в контрактах текущего арендатора.
 * Пример: «найти все договоры, где мы платим за доставку».
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SemanticSearchService searchService;

    public SearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public List<ChunkMatch> search(@Valid @RequestBody SearchRequest request) {
        return searchService.search(request.query());
    }
}
