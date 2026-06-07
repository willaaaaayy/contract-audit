package com.contractaudit.compliance;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Compliance Checker по документу: сверка с политиками компании. Всё tenant-scoped через
 * {@code @TenantId} — чужой документ/политики недоступны.
 */
@RestController
@RequestMapping("/api/documents/{documentId}/compliance")
public class ComplianceController {

    private final ComplianceCheckService complianceCheckService;

    public ComplianceController(ComplianceCheckService complianceCheckService) {
        this.complianceCheckService = complianceCheckService;
    }

    /** Запустить (или перезапустить) сверку обработанного документа с политиками. */
    @PostMapping
    public List<ComplianceFindingResponse> check(@PathVariable UUID documentId) {
        return complianceCheckService.check(documentId).stream()
                .map(ComplianceFindingResponse::from).toList();
    }

    @GetMapping
    public List<ComplianceFindingResponse> list(@PathVariable UUID documentId) {
        return complianceCheckService.findByDocument(documentId).stream()
                .map(ComplianceFindingResponse::from).toList();
    }
}
