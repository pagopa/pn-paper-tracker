package it.pagopa.pn.papertracker.model;

import lombok.Getter;

public enum ErrorCategory {

    RENDICONTAZIONE_SCARTATA("Rendicontazione scartata"),
    UNKNOWN("Errore non categorizzato");

    private final String value;

    ErrorCategory(String value) {
        this.value = value;
    }

}
