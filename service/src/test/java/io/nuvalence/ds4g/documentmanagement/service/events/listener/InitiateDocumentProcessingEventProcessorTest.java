package io.nuvalence.ds4g.documentmanagement.service.events.listener;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.nuvalence.ds4g.documentmanagement.service.service.DocumentProcessingService;
import io.nuvalence.events.event.EventMetadata;
import io.nuvalence.events.event.InitiateDocumentProcessingEvent;
import io.nuvalence.events.event.dto.ProcessorId;
import io.nuvalence.events.exception.EventProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class InitiateDocumentProcessingEventProcessorTest {

    private InitiateDocumentProcessingEventProcessor initiateDocumentProcessingEventProcessor;

    @Mock private DocumentProcessingService documentProcessingService;

    @BeforeEach
    void setUp() {

        initiateDocumentProcessingEventProcessor =
                new InitiateDocumentProcessingEventProcessor(documentProcessingService);
    }

    @Test
    void testExecute() throws EventProcessingException {

        InitiateDocumentProcessingEvent event = createTestEvent();

        initiateDocumentProcessingEventProcessor.execute(event);

        verify(documentProcessingService).initEnqueueDocumentProcessingRequest(any(), any(), any());
    }

    @Test
    void testExecute_exception() {

        InitiateDocumentProcessingEvent event = createTestEvent();

        doThrow(new RuntimeException("Error processing document"))
                .when(documentProcessingService)
                .initEnqueueDocumentProcessingRequest(any(), any(), any());

        assertThrows(
                EventProcessingException.class,
                () -> {
                    initiateDocumentProcessingEventProcessor.execute(event);
                });
    }

    private InitiateDocumentProcessingEvent createTestEvent() {

        EventMetadata metadata =
                EventMetadata.builder()
                        .id(UUID.randomUUID())
                        .type("initiateDocumentProcessing")
                        .originatorId(UUID.randomUUID().toString())
                        .userId(UUID.randomUUID())
                        .timestamp(OffsetDateTime.now())
                        .correlationId("correlationId")
                        .build();

        ProcessorId processorIdOne = ProcessorId.builder().processorId("processorIdOne").build();

        ProcessorId processorIdTwo = ProcessorId.builder().processorId("processorIdTwo").build();

        InitiateDocumentProcessingEvent event =
                InitiateDocumentProcessingEvent.builder()
                        .documentId(UUID.randomUUID())
                        .processorIds(List.of(processorIdOne, processorIdTwo))
                        .build();

        event.setMetadata(metadata);

        return event;
    }
}
