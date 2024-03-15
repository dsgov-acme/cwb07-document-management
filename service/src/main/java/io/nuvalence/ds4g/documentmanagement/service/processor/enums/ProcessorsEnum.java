package io.nuvalence.ds4g.documentmanagement.service.processor.enums;

import lombok.Getter;

/**
 * Enumerates the available document processors.
 */
@Getter
public enum ProcessorsEnum {
    DOC_AI_ID_PROOFING("docai-id-proofing", "ID Proofing", "ID Proofing Processor"),
    DOC_AI_DOCUMENT_QUALITY(
            "docai-document-quality", "Document Quality", "Document Quality Processor");

    private final String id;
    private final String name;
    private final String description;

    ProcessorsEnum(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }
}
