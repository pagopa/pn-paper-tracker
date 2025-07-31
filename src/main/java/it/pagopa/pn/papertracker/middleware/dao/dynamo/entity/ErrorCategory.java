package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

public enum ErrorCategory {

    RENDICONTAZIONE_SCARTATA("Rendicontazione scartata"),
    UNKNOWN("Errore non categorizzato"),
    DATE_ERROR("Errore nella validazione della tripletta.");

    private final String value;

    ErrorCategory(String value) {
        this.value = value;
    }

}
