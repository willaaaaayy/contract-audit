package com.contractaudit.document.processing;

import com.contractaudit.document.processing.ProcessingJobRepository.ClaimableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Долговечный драйвер обработки: периодически забирает документы, ждущие обработки —
 * новые ({@code PENDING}) и зависшие после краша ({@code PROCESSING} дольше таймаута) — и
 * отправляет их в {@link DocumentProcessingService}. Захват документа атомарен внутри
 * {@code process(...)}, поэтому дубль-диспатч безвреден.
 *
 * <p>Выключается через {@code processing.poller.enabled=false} (например, в тестах, где
 * обработку запускают вручную).
 */
@Component
@ConditionalOnProperty(prefix = "processing.poller", name = "enabled", matchIfMissing = true)
public class DocumentProcessingPoller {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingPoller.class);

    private final ProcessingJobRepository jobRepository;
    private final DocumentProcessingService processingService;
    private final ProcessingProperties properties;

    public DocumentProcessingPoller(ProcessingJobRepository jobRepository,
                                    DocumentProcessingService processingService,
                                    ProcessingProperties properties) {
        this.jobRepository = jobRepository;
        this.processingService = processingService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${processing.poll-interval-ms:5000}",
            initialDelayString = "${processing.poll-interval-ms:5000}")
    public void poll() {
        Instant staleBefore = Instant.now().minus(Duration.ofSeconds(properties.staleAfterSeconds()));
        List<ClaimableJob> jobs = jobRepository.findClaimable(staleBefore, properties.pollBatchSize());
        if (jobs.isEmpty()) {
            return;
        }
        log.debug("Поллер: {} документ(ов) к обработке", jobs.size());
        for (ClaimableJob job : jobs) {
            processingService.process(job.tenantId(), job.documentId());   // @Async, сам захватит
        }
    }
}
