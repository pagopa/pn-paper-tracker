package it.pagopa.pn.papertracker.model;

import lombok.Getter;

import java.util.Set;

@Getter
public enum OcrDocumentTypeEnum {

    AR(Set.of(DocumentTypeEnum.AR, DocumentTypeEnum.PLICO)),
    RIR(Set.of(DocumentTypeEnum.AR, DocumentTypeEnum.PLICO));

    private final Set<DocumentTypeEnum> documentTypes;

    OcrDocumentTypeEnum(Set<DocumentTypeEnum> documentTypes) {
        this.documentTypes = documentTypes;
    }
}
