package io.nuvalence.ds4g.documentmanagement.service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.nuvalence.auth.access.AuthorizationHandler;
import io.nuvalence.ds4g.documentmanagement.service.entity.DocumentProcessorResult;
import io.nuvalence.ds4g.documentmanagement.service.exceptions.NotAvailableException;
import io.nuvalence.ds4g.documentmanagement.service.exceptions.ProvidedDataException;
import io.nuvalence.ds4g.documentmanagement.service.generated.models.DocumentProcessingRequest;
import io.nuvalence.ds4g.documentmanagement.service.generated.models.ProcessorInfo;
import io.nuvalence.ds4g.documentmanagement.service.model.ScanStatus;
import io.nuvalence.ds4g.documentmanagement.service.model.ScanStatusResponse;
import io.nuvalence.ds4g.documentmanagement.service.service.DocumentProcessingService;
import io.nuvalence.ds4g.documentmanagement.service.service.DocumentService;
import io.nuvalence.ds4g.documentmanagement.service.storage.StorageProvider;
import io.nuvalence.events.event.dto.ProcessorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
class DocumentsApiDelegateImplTest {
    @Mock private DocumentsApiDelegateImpl apiDelegate;

    @Mock private AuthorizationHandler authorizationHandler;

    @Mock private DocumentService documentService;

    @Mock private DocumentProcessingService documentProcessingService;

    @Mock private StorageProvider storage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(documentProcessingService, "storage", storage);
        apiDelegate =
                new DocumentsApiDelegateImpl(
                        storage,
                        authorizationHandler,
                        new ArrayList<>(),
                        new ArrayList<>(),
                        documentService,
                        documentProcessingService);
    }

    private void stubCreatePermissions() {
        when(authorizationHandler.isAllowed("create", DocumentProcessorResult.class))
                .thenReturn(true);
    }

    @Test
    void testEnqueueDocumentProcessingRequest_whenScanStatusIsFailed() {
        stubCreatePermissions();
        String id = UUID.randomUUID().toString();
        ScanStatusResponse response = new ScanStatusResponse(ScanStatus.FAILED_SCAN);

        doCallRealMethod().when(documentProcessingService).handleReprocessing(any(), any(), any());
        doCallRealMethod()
                .when(documentProcessingService)
                .initEnqueueDocumentProcessingRequest(any(), any(), any());
        when(storage.getStatus(id)).thenReturn(response.getScanStatus());

        List<DocumentProcessingRequest> documentProcessingRequests = new ArrayList<>();
        documentProcessingRequests.add(new DocumentProcessingRequest());

        assertThrows(
                NotAvailableException.class,
                () -> {
                    apiDelegate.enqueueDocumentProcessingRequest(
                            id, documentProcessingRequests, false);
                });
    }

    @Test
    void testEnqueueDocumentProcessingRequest_succeeds() throws JsonProcessingException {
        stubCreatePermissions();
        List<DocumentProcessingRequest> documentProcessingRequests = new ArrayList<>();
        documentProcessingRequests.add(new DocumentProcessingRequest());
        ScanStatusResponse response = new ScanStatusResponse(ScanStatus.READY);

        doCallRealMethod().when(documentProcessingService).handleReprocessing(any(), any(), any());
        doCallRealMethod()
                .when(documentProcessingService)
                .initEnqueueDocumentProcessingRequest(any(), any(), any());

        String id = UUID.randomUUID().toString();

        when(storage.getStatus(id)).thenReturn(response.getScanStatus());
        doNothing()
                .when(documentProcessingService)
                .enqueueDocumentProcessingRequest(eq(id), any(List.class));

        ResponseEntity<Void> result =
                apiDelegate.enqueueDocumentProcessingRequest(id, documentProcessingRequests, false);

        assertEquals(200, result.getStatusCodeValue());
    }

    @Test
    void testEnqueueDocumentProcessingRequest_exception() throws JsonProcessingException {
        stubCreatePermissions();
        List<DocumentProcessingRequest> documentProcessingRequests = new ArrayList<>();
        documentProcessingRequests.add(new DocumentProcessingRequest());
        ScanStatusResponse response = new ScanStatusResponse(ScanStatus.READY);

        JsonProcessingException exception = mock(JsonProcessingException.class);

        doCallRealMethod().when(documentProcessingService).handleReprocessing(any(), any(), any());
        doCallRealMethod()
                .when(documentProcessingService)
                .initEnqueueDocumentProcessingRequest(any(), any(), any());

        String id = UUID.randomUUID().toString();

        when(storage.getStatus(id)).thenReturn(response.getScanStatus());
        doThrow(exception)
                .when(documentProcessingService)
                .enqueueDocumentProcessingRequest(eq(id), any(List.class));

        assertThrows(
                ProvidedDataException.class,
                () -> {
                    apiDelegate.enqueueDocumentProcessingRequest(
                            id, documentProcessingRequests, false);
                });
    }

    @Test
    void testEnqueueDocumentProcessingRequest_noProcessingDataProvided()
            throws JsonProcessingException {
        stubCreatePermissions();
        String id = UUID.randomUUID().toString();
        List<DocumentProcessingRequest> documentProcessingRequests = new ArrayList<>();

        ResponseEntity<Void> result =
                apiDelegate.enqueueDocumentProcessingRequest(id, documentProcessingRequests, false);

        assertEquals(400, result.getStatusCodeValue());
    }

    @Test
    void testEnqueueDocumentProcessingRequest_reprocessing_success()
            throws JsonProcessingException {
        stubCreatePermissions();
        List<DocumentProcessingRequest> documentProcessingRequests = new ArrayList<>();
        documentProcessingRequests.add(new DocumentProcessingRequest());
        ScanStatusResponse response = new ScanStatusResponse(ScanStatus.READY);

        doCallRealMethod().when(documentProcessingService).handleReprocessing(any(), any(), any());
        doCallRealMethod()
                .when(documentProcessingService)
                .initEnqueueDocumentProcessingRequest(any(), any(), any());

        String id = UUID.randomUUID().toString();

        when(storage.getStatus(id)).thenReturn(response.getScanStatus());
        doNothing()
                .when(documentProcessingService)
                .enqueueDocumentProcessingRequest(eq(id), any(List.class));

        ResponseEntity<Void> result =
                apiDelegate.enqueueDocumentProcessingRequest(id, documentProcessingRequests, true);

        assertEquals(200, result.getStatusCodeValue());
    }

    @Test
    void testEnqueueDocumentProcessingRequest_avoid_reprocessing_all_results_already_exist_success()
            throws JsonProcessingException {
        stubCreatePermissions();
        String id = UUID.randomUUID().toString();
        List<DocumentProcessingRequest> documentProcessingRequests =
                createDocumentProcessingRequests();

        List<DocumentProcessorResult> existingProcessingResults =
                createDocumentProcessorResults(id, documentProcessingRequests);

        doCallRealMethod().when(documentProcessingService).handleReprocessing(any(), any(), any());
        doCallRealMethod()
                .when(documentProcessingService)
                .initEnqueueDocumentProcessingRequest(any(), any(), any());
        when(documentProcessingService.findByDocumentIdAndListOfProcessorIds(
                        eq(UUID.fromString(id)), any(List.class)))
                .thenReturn(existingProcessingResults);

        ResponseEntity<Void> result =
                apiDelegate.enqueueDocumentProcessingRequest(id, documentProcessingRequests, false);

        verify(documentProcessingService, never())
                .enqueueDocumentProcessingRequest(eq(id), any(ArrayList.class));
        assertEquals(200, result.getStatusCodeValue());
    }

    @Test
    void
            testEnqueueDocumentProcessingRequest_avoid_reprocessing_some_results_already_exist_success()
                    throws JsonProcessingException {
        stubCreatePermissions();
        String id = UUID.randomUUID().toString();
        List<DocumentProcessingRequest> documentProcessingRequests =
                createDocumentProcessingRequests();

        DocumentProcessorResult existingResult =
                DocumentProcessorResult.builder()
                        .documentId(UUID.fromString(id))
                        .processorId(documentProcessingRequests.get(0).getProcessorId())
                        .build();
        List<DocumentProcessorResult> existingProcessingResults =
                Collections.singletonList(existingResult);

        doCallRealMethod().when(documentProcessingService).handleReprocessing(any(), any(), any());
        doCallRealMethod()
                .when(documentProcessingService)
                .initEnqueueDocumentProcessingRequest(any(), any(), any());
        when(documentProcessingService.findByDocumentIdAndListOfProcessorIds(
                        eq(UUID.fromString(id)), any(List.class)))
                .thenReturn(existingProcessingResults);
        ScanStatusResponse response = new ScanStatusResponse(ScanStatus.READY);

        when(storage.getStatus(id)).thenReturn(response.getScanStatus());
        doNothing()
                .when(documentProcessingService)
                .enqueueDocumentProcessingRequest(eq(id), any(List.class));

        ArgumentCaptor<List<ProcessorId>> processingRequestsCaptor =
                ArgumentCaptor.forClass(List.class);

        ResponseEntity<Void> result =
                apiDelegate.enqueueDocumentProcessingRequest(id, documentProcessingRequests, false);

        verify(documentProcessingService)
                .enqueueDocumentProcessingRequest(eq(id), processingRequestsCaptor.capture());
        assertEquals(1, processingRequestsCaptor.getValue().size());
        assertEquals(200, result.getStatusCodeValue());
    }

    @Test
    void testGetDocumentProcessingResults_returnsGoneForFailedScanFiles() {
        final String id = UUID.randomUUID().toString();
        when(storage.getStatus(id)).thenReturn(ScanStatus.FAILED_SCAN);

        assertThrows(
                NotAvailableException.class,
                () -> {
                    apiDelegate.getDocumentProcessingResults(id);
                });
    }

    @Test
    void testGetDocumentProcessors() throws JsonProcessingException {
        when(authorizationHandler.isAllowed("view", DocumentProcessorResult.class))
                .thenReturn(true);

        ResponseEntity<List<ProcessorInfo>> result = apiDelegate.getDocumentProcessors();
        List<ProcessorInfo> processors = result.getBody();

        assertEquals(2, processors.size());
        assertEquals("docai-id-proofing", processors.get(0).getId());
        assertEquals("docai-document-quality", processors.get(1).getId());
        assertEquals(200, result.getStatusCodeValue());
    }

    private List<DocumentProcessorResult> createDocumentProcessorResults(
            String id, List<DocumentProcessingRequest> documentProcessingRequests) {
        return documentProcessingRequests.stream()
                .map(
                        x ->
                                DocumentProcessorResult.builder()
                                        .documentId(UUID.fromString(id))
                                        .processorId(x.getProcessorId())
                                        .build())
                .collect(Collectors.toList());
    }

    private List<DocumentProcessingRequest> createDocumentProcessingRequests() {
        DocumentProcessingRequest processingRequest1 = new DocumentProcessingRequest();
        processingRequest1.setProcessorId("processor1");
        DocumentProcessingRequest processingRequest2 = new DocumentProcessingRequest();
        processingRequest2.setProcessorId("processor2");

        return Arrays.asList(processingRequest1, processingRequest2);
    }
}
