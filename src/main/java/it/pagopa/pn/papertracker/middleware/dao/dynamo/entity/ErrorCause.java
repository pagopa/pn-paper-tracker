package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCause {

    GIACENZA_DATE_ERROR("Date di inizio e fine giacenza non coerenti"),
    OCR_DUPLICATED_EVENT("Evento di risposta duplicato"),
    OCR_DRY_RUN_MODE("Evento di risposta ignorato in quanto attiva la modalità dry-run per l'OCR"),
    OCR_KO( "Errore nella validazione dell'OCR"),
    OCR_UNSUPPORTED_PRODUCT("Prodotto non supportato per la validazione OCR"),
    STOCK_890_REFINEMENT_MISSING("Spedizione 890 non perfezionata"),
    STOCK_890_REFINEMENT_ERROR("Errore nel perfezionamento della spedizione 890"),;

    private final String description;

}
