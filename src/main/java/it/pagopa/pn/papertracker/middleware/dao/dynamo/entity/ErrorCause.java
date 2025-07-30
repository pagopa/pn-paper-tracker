package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCause {

    GIACENZA_DATE_ERROR("GIACENZA_DATE_ERROR", "Date di inizio e fine giacenza non coerenti"),
    UNKNOWN("UNKNOWN", "Causa errore sconosciuta");

    private final String value;
    private final String description;

}
