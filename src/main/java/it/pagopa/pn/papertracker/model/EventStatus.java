package it.pagopa.pn.papertracker.model;

public enum EventStatus {

    KO("KO"),
    PROGRESS("PROGRESS"),
    OK("OK");

    private String value;

    EventStatus(String value) {
        this.value = value;
    }
}
