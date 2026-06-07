package com.contractaudit.negotiation;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Предложения по правкам договора (Negotiation Assistant). Tenant-scoped через {@code @TenantId}
 * на рисках — чужой документ не отдаст ни рисков, ни предложений.
 */
@RestController
@RequestMapping("/api/documents/{documentId}/suggestions")
public class NegotiationController {

    private final NegotiationService negotiationService;

    public NegotiationController(NegotiationService negotiationService) {
        this.negotiationService = negotiationService;
    }

    /**
     * Сгенерировать предложения по переписыванию рискованных пунктов.
     *
     * @param all true — по всем рискам; по умолчанию только HIGH/CRITICAL
     */
    @PostMapping
    public List<ClauseSuggestion> suggest(@PathVariable UUID documentId,
                                          @RequestParam(name = "all", defaultValue = "false") boolean all) {
        return negotiationService.suggest(documentId, all);
    }
}
