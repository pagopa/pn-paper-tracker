package it.pagopa.pn.papertracker.model;

import lombok.Getter;

@Getter
public enum DocumentTypeEnum {
    PLICO("Plico"),
    AR("AR"),
    INDAGINE("Indagine");

    private final String value;

    DocumentTypeEnum(String value) {
        this.value = value;
    }
}
