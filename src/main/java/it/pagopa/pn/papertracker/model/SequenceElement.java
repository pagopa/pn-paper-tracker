package it.pagopa.pn.papertracker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
public class SequenceElement {

    private String code;
    private Set<DocumentTypeEnum> requiredDocumentType;
    private boolean optional;
    private String dateValidationGroup;

}
