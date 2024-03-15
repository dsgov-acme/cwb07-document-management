package io.nuvalence.ds4g.documentmanagement.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nuvalence.ds4g.documentmanagement.service.config.PublishDocumentProcessingConfig;
import io.nuvalence.ds4g.documentmanagement.service.config.PublishDocumentProcessingResultConfig;
import io.nuvalence.ds4g.documentmanagement.service.entity.DocumentProcessorResult;
import io.nuvalence.ds4g.documentmanagement.service.entity.DocumentProcessorStatus;
import io.nuvalence.ds4g.documentmanagement.service.exceptions.NotAvailableException;
import io.nuvalence.ds4g.documentmanagement.service.exceptions.ProvidedDataException;
import io.nuvalence.ds4g.documentmanagement.service.model.ScanStatus;
import io.nuvalence.ds4g.documentmanagement.service.model.ScanStatusResponse;
import io.nuvalence.ds4g.documentmanagement.service.processor.DocumentProcessingRequestWrapper;
import io.nuvalence.ds4g.documentmanagement.service.processor.DocumentProcessor;
import io.nuvalence.ds4g.documentmanagement.service.processor.DocumentProcessorRegistry;
import io.nuvalence.ds4g.documentmanagement.service.processor.exception.RetryableDocumentProcessingException;
import io.nuvalence.ds4g.documentmanagement.service.processor.exception.UnretryableDocumentProcessingException;
import io.nuvalence.ds4g.documentmanagement.service.repository.DocumentProcessorResultRepository;
import io.nuvalence.ds4g.documentmanagement.service.storage.StorageProvider;
import io.nuvalence.events.event.dto.ProcessorId;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for processing documents.
 */
@Service
@Slf4j
public class DocumentProcessingService {

    private final PublishDocumentProcessingConfig.DocumentProcessingPublisher
            documentProcessingPublisher;

    private final PublishDocumentProcessingResultConfig.DocumentProcessingResultPublisher
            documentProcessingResultPublisher;

    private final DocumentProcessorRegistry documentProcessorRegistry;

    private final ObjectMapper mapper;

    private final DocumentProcessorResultRepository documentProcessorResultRepository;

    private final StorageProvider storage;

    /**
     * Constructor for this service.
     *  @param documentProcessingPublisher PubSub publisher for processing requests.
     * @param documentProcessingResultPublisher PubSub publisher for processing results.
     * @param documentProcessorRegistry Registry of document processors.
     * @param mapper Jackson mapper.
     * @param documentProcessorResultRepository repository for document processing results.
     * @param storage storage provider.
     *
     */
    public DocumentProcessingService(
            PublishDocumentProcessingConfig.DocumentProcessingPublisher documentProcessingPublisher,
            PublishDocumentProcessingResultConfig.DocumentProcessingResultPublisher
                    documentProcessingResultPublisher,
            DocumentProcessorRegistry documentProcessorRegistry,
            ObjectMapper mapper,
            DocumentProcessorResultRepository documentProcessorResultRepository,
            StorageProvider storage) {
        this.documentProcessingPublisher = documentProcessingPublisher;
        this.documentProcessingResultPublisher = documentProcessingResultPublisher;
        this.documentProcessorRegistry = documentProcessorRegistry;
        this.mapper = mapper;
        this.documentProcessorResultRepository = documentProcessorResultRepository;
        this.storage = storage;
    }

    /**
     * Creates a Document Entity from a document model.
     *
     * @param documentId ID of the document to process.
     * @param documentProcessingRequests processing request information.
     * @throws JsonProcessingException failed parsing.
     */
    public void enqueueDocumentProcessingRequest(
            String documentId, List<ProcessorId> documentProcessingRequests)
            throws JsonProcessingException {

        for (ProcessorId processorId : documentProcessingRequests) {

            documentProcessingPublisher.publish(
                    mapper.writeValueAsString(
                            DocumentProcessingRequestWrapper.builder()
                                    .request(processorId)
                                    .documentId(documentId)
                                    .build()));

            documentProcessorResultRepository.save(
                    DocumentProcessorResult.builder()
                            .documentId(UUID.fromString(documentId))
                            .processorId(processorId.getProcessorId())
                            .status(DocumentProcessorStatus.PENDING)
                            .result(Map.of())
                            .timestamp(OffsetDateTime.now())
                            .build());

            log.debug("Publish request for processing {}", processorId);
        }
    }

    /**
     * Syncronous method for processing a document. 
     * It either fails or completes call execution, returning a result with relevant
     * information and status (which can be used to determine if it was successful or not).
     *
     * @param documentId ID of the document to process.
     * @param processorId an AI document processor id.
     * @return DocumentProcessorResult of the processing. Never returns null.
     * @throws JsonProcessingException failed parsing.
     * @throws UnretryableDocumentProcessingException failed processing due to an unrecoverable exception.
     * @throws RetryableDocumentProcessingException failed processing due to a recoverable exception.
     */
    public @NotNull DocumentProcessorResult processRequest(String documentId, String processorId)
            throws JsonProcessingException, UnretryableDocumentProcessingException,
                    RetryableDocumentProcessingException {
        Optional<DocumentProcessor> processor = documentProcessorRegistry.getProcessor(processorId);

        if (processor.isEmpty()) {
            createUnprocessableResultAndUpdateRecord(
                    documentId, processorId, "processor not found");

            log.error(
                    String.format(
                            "Processor not found %s, for document %s", processorId, documentId));
            throw new UnretryableDocumentProcessingException("Processor not found");
        }

        var result = processor.get().process(documentId);

        // avoiding unnecessary long retry over pubsub if dependency is solved quickly
        if (result.getStatus() == DocumentProcessorStatus.MISSING_DEPENDENCY) {
            log.warn(
                    String.format(
                            "Dependency missing for processing document %s. This operation will be"
                                    + " retried",
                            documentId));
            throw new RetryableDocumentProcessingException("Missing dependency");
        }

        if (result.getStatus() == DocumentProcessorStatus.UNPROCESSABLE) {
            String unretryableErrorMessage =
                    "An unretryable error occurred for document: " + documentId;
            if (result.getResult() instanceof Map) {
                String errorMessage = (String) ((Map) result.getResult()).get("error");
                log.error("{}: {}", unretryableErrorMessage, errorMessage);
                createUnprocessableResultAndUpdateRecord(
                        documentId, processorId, "Unprocessable document");
                throw new UnretryableDocumentProcessingException(errorMessage);
            }
            log.error(unretryableErrorMessage);
            createUnprocessableResultAndUpdateRecord(
                    documentId, processorId, "Unprocessable document");
            throw new UnretryableDocumentProcessingException(unretryableErrorMessage);
        }

        if (result.getStatus() != DocumentProcessorStatus.COMPLETE) {
            throw new RetryableDocumentProcessingException(
                    String.format(
                            "Document %s request could not be completed and will be retried."
                                    + " Status: %s",
                            documentId, result.getStatus()));
        }

        result.setProcessorId(processor.get().getProcessorId());

        documentProcessorResultRepository.save(result);

        documentProcessingResultPublisher.publish(mapper.writeValueAsString(result));
        log.debug(
                "Document processing result was successfully published {}",
                result.getProcessorId());

        return result;
    }

    private DocumentProcessorResult<Object> createUnprocessableResultAndUpdateRecord(
            String documentId, String processorId, String error) {
        var result =
                DocumentProcessorResult.builder()
                        .documentId(UUID.fromString(documentId))
                        .processorId(processorId)
                        .status(DocumentProcessorStatus.UNPROCESSABLE)
                        .result(Map.of("error", error))
                        .timestamp(OffsetDateTime.now())
                        .build();

        documentProcessorResultRepository.save(result);
        return result;
    }

    /**
     * Retrieves a list of document processing results for a given document.
     *
     * @param documentId ID of the document whose results are to be recovered.
     * @return list of processing results.
     */
    public List<DocumentProcessorResult> getProcessingResultsForDocument(UUID documentId) {

        return documentProcessorResultRepository.findByDocumentId(documentId).stream()
                .sorted(Comparator.comparing(DocumentProcessorResult::getTimestamp))
                .toList();
    }

    /**
     * Saves a document processing result.
     *
     * @param result result to be saved.
     */
    public void saveDocumentProcessingResult(DocumentProcessorResult result) {
        documentProcessorResultRepository.save(result);
    }

    public List<DocumentProcessorResult> findByDocumentIdAndListOfProcessorIds(
            UUID documentId, List<String> processorIds) {
        return documentProcessorResultRepository.findByDocumentIdAndProcessorIdIn(
                documentId, processorIds);
    }

    /**
     * Handles reprocessing of a document.
     * @param documentId document id
     * @param documentProcessingRequests list of processors to reprocess
     * @param reprocess if true, reprocess all processors
     * @return list of processors that will be reprocessed
     */
    public List<ProcessorId> handleReprocessing(
            String documentId, List<ProcessorId> documentProcessingRequests, Boolean reprocess) {

        if (Boolean.FALSE.equals(reprocess)) {
            List<DocumentProcessorResult> existingProcessingResults =
                    findByDocumentIdAndListOfProcessorIds(
                            UUID.fromString(documentId),
                            documentProcessingRequests.stream()
                                    .map(ProcessorId::getProcessorId)
                                    .toList());

            List<ProcessorId> filteredDocumentProcessingRequests =
                    documentProcessingRequests.stream()
                            .filter(
                                    request ->
                                            existingProcessingResults.stream()
                                                    .noneMatch(
                                                            result ->
                                                                    result.getProcessorId()
                                                                            .equals(
                                                                                    request
                                                                                            .getProcessorId())))
                            .toList();

            if (!existingProcessingResults.isEmpty()) {
                log.info(
                        "Document {} already processed by the following processors: {}, request"
                                + " discarded for them",
                        documentId,
                        existingProcessingResults.stream()
                                .map(DocumentProcessorResult::getProcessorId)
                                .toList());
            }

            return filteredDocumentProcessingRequests;
        }
        return documentProcessingRequests;
    }

    /**
     * Enqueues a document processing request.
     * @param documentId ID of the document to process.
     * @param processorIds list of processors to process the document with
     * @param reprocess if true, reprocess all processors
     * @throws ProvidedDataException if the message could not be enqueued.
     * @throws NotAvailableException Document has been permanently quarantined and cannot be retrieved.
     */
    public void initEnqueueDocumentProcessingRequest(
            String documentId, List<ProcessorId> processorIds, Boolean reprocess) {
        processorIds = handleReprocessing(documentId, processorIds, reprocess);

        if (!processorIds.isEmpty()) {
            ScanStatusResponse status = new ScanStatusResponse(storage.getStatus(documentId));

            if (status.getScanStatus().equals(ScanStatus.FAILED_SCAN)) {
                throw new NotAvailableException(ScanStatus.FAILED_SCAN.getMessage());
            }

            log.debug("Enqueuing processing request for document with id {}", documentId);

            try {
                enqueueDocumentProcessingRequest(documentId, processorIds);
            } catch (JsonProcessingException e) {
                throw new ProvidedDataException(
                        "The message with id " + documentId + " could not be enqueued");
            }
        }
    }
}
