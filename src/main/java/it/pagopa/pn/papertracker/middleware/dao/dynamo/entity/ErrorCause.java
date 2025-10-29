package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCause {

    GIACENZA_DATE_ERROR("Date di inizio e fine giacenza non coerenti"),
    OCR_DUPLICATED_EVENT("Evento di risposta duplicato"),
    OCR_KO( "Errore nella validazione dell'OCR"),
    OCR_UNSUPPORTED_PRODUCT("Prodotto non supportato per la validazione OCR"),
    GIACENZA_RECAG012_ERROR("Spedizione 890 non perfezionata in giacenza");

    private final String description;

}
