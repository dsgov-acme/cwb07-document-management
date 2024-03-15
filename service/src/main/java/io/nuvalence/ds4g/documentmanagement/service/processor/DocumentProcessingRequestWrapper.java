package io.nuvalence.ds4g.documentmanagement.service.processor;

import io.nuvalence.events.event.dto.ProcessorId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *  Wrapper class for processing results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessingRequestWrapper {
    private ProcessorId request;
    private String documentId;
}
