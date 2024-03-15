package io.nuvalence.ds4g.documentmanagement.service.events.listener;

import io.nuvalence.ds4g.documentmanagement.service.service.DocumentProcessingService;
import io.nuvalence.events.event.InitiateDocumentProcessingEvent;
import io.nuvalence.events.exception.EventProcessingException;
import io.nuvalence.events.subscriber.EventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Event processor for initiating document processing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InitiateDocumentProcessingEventProcessor
        implements EventProcessor<InitiateDocumentProcessingEvent> {

    private final DocumentProcessingService documentProcessingService;

    @Override
    public void execute(InitiateDocumentProcessingEvent event) throws EventProcessingException {
        log.debug(
                "Received event {} of type {}",
                event.getMetadata().getId(),
                event.getMetadata().getType());

        try {
            documentProcessingService.initEnqueueDocumentProcessingRequest(
                    event.getDocumentId().toString(), event.getProcessorIds(), false);
        } catch (Exception e) {
            log.error("Error processing document", e);
            throw new EventProcessingException(e);
        }
    }

    @Override
    public Class<InitiateDocumentProcessingEvent> getEventClass() {
        return InitiateDocumentProcessingEvent.class;
    }
}
