package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCause {

    GIACENZA_DATE_ERROR("GIACENZA_DATE_ERROR", "Date di inizio e fine giacenza non coerenti"),
    UNKNOWN("UNKNOWN", "Causa errore sconosciuta"),
    DATE_ERROR("DATE_ERROR", "Errore nella validazione delle date della sequenza."),
    STATUS_CODE_ERROR("STATUS_CODE_ERROR", "Errore nella validazione della presenza degli elementi della sequenza."),
    LAST_EVENT_EXTRACTION_ERROR("LAST_EVENT_EXTRACTION_ERROR", "Errore nell'estrazione della sequenza dall'ultimo evento."),
    REGISTERED_LETTER_CODE_ERROR("REGISTERED_LETTER_CODE_ERROR", "Errore nella validazione del registered letter code"),
    DELIVERY_FAILURE_CAUSE_ERROR("DELIVERY_FAILURE_CAUSE_ERROR", "Errore nella validazione del delivery Failure Cause"),
    ATTACHMENTS_ERROR("ATTACHMENTS_ERROR", "Errore nella validazione degli allegati della sequenza");

    private final String value;
    private final String description;

}
