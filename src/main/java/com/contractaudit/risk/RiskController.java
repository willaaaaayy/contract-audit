package com.contractaudit.risk;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Risk Scanner по документу. Всё tenant-scoped через {@code @TenantId}: чужой документ
 * не отдаст ни рисков, ни возможности их сканировать.
 */
@RestController
@RequestMapping("/api/documents/{documentId}/risks")
public class RiskController {

    private final RiskScanService riskScanService;

    public RiskController(RiskScanService riskScanService) {
        this.riskScanService = riskScanService;
    }

    /** Запустить (или перезапустить) анализ рисков по уже обработанному документу. */
    @PostMapping
    public List<RiskResponse> scan(@PathVariable UUID documentId) {
        return riskScanService.scan(documentId).stream().map(RiskResponse::from).toList();
    }

    @GetMapping
    public List<RiskResponse> list(@PathVariable UUID documentId) {
        return riskScanService.findByDocument(documentId).stream().map(RiskResponse::from).toList();
    }
}
