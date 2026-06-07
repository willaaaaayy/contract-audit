package com.contractaudit.document.processing;

import com.contractaudit.document.processing.ProcessingJobRepository.ClaimableJob;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Поллер — тонкий диспатчер: находит claimable документы и отправляет каждый в обработку
 * (захват происходит уже внутри process). Проверяем именно это, без БД и async.
 */
class DocumentProcessingPollerTest {

    private final ProcessingProperties properties = new ProcessingProperties(1200, 64, 300, 10);

    @Test
    void poll_dispatchesEveryClaimableJob() {
        ProcessingJobRepository jobRepository = mock(ProcessingJobRepository.class);
        DocumentProcessingService processingService = mock(DocumentProcessingService.class);
        DocumentProcessingPoller poller = new DocumentProcessingPoller(jobRepository, processingService, properties);

        UUID tenant1 = UUID.randomUUID();
        UUID doc1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        when(jobRepository.findClaimable(any(Instant.class), eq(10)))
                .thenReturn(List.of(new ClaimableJob(doc1, tenant1), new ClaimableJob(doc2, tenant2)));

        poller.poll();

        verify(processingService).process(tenant1, doc1);
        verify(processingService).process(tenant2, doc2);
    }

    @Test
    void poll_doesNothingWhenQueueEmpty() {
        ProcessingJobRepository jobRepository = mock(ProcessingJobRepository.class);
        DocumentProcessingService processingService = mock(DocumentProcessingService.class);
        DocumentProcessingPoller poller = new DocumentProcessingPoller(jobRepository, processingService, properties);

        when(jobRepository.findClaimable(any(Instant.class), eq(10))).thenReturn(List.of());

        poller.poll();

        verifyNoInteractions(processingService);
    }
}
