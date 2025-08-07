package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCause {

    GIACENZA_DATE_ERROR("Date di inizio e fine giacenza non coerenti"),
    OCR_KO( "Errore nella validazione dell'OCR");

    private final String description;

}
