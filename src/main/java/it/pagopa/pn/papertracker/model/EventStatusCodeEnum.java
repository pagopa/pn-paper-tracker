package it.pagopa.pn.papertracker.model;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import lombok.Getter;

import java.util.List;
import java.util.stream.Stream;

import static it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum.*;

@Getter
public enum EventStatusCodeEnum {
    // AR
    RECRN006(EventTypeEnum.RETRYABLE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(F01, F02, F03, F04), "Furto/Smarrimento/deterioramento", false),
    RECRN013(EventTypeEnum.RETRYABLE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Non Rendicontabile ", false),
    RECRN015(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(C01, C02, C03, C04, C05, C06), "Causa Forza Maggiore", false),
    RECRN001C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Consegnato - Fascicolo Chiuso", false),
    RECRN002C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Mancata consegna - Fascicolo Chiuso", false),
    RECRN002F(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.KO, List.of(), "Irreperibilità Assoluta - Fascicolo Chiuso", false),
    RECRN003C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Consegnato presso Punti di Giacenza - Fascicolo Chiuso", false),
    RECRN004C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Mancata consegna presso Punti di Giacenza - Fascicolo Chiuso", false),
    RECRN005C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Compiuta giacenza - Fascicolo Chiuso", false),
    RECRN011(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "In giacenza", false),
    RECRN001A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Consegnato - pre-esito", false),
    RECRN002A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(M02, M05, M06, M07, M08, M09), "Mancata consegna - pre-esito", false),
    RECRN002D(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(M01, M03, M04), "Irreperibilità Assoluta - pre-esito", false),
    RECRN003A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Consegnato presso Punti di Giacenza - pre-esito", false),
    RECRN004A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Mancata consegna presso Punti di Giacenza - pre-esito", false),
    RECRN005A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Compiuta giacenza pre-esito", false),
    RECRN001B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Consegnato - In Dematerializzazione", true),
    RECRN002B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Mancata consegna - In Dematerializzazione", true),
    RECRN002E(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Irreperibilità Assoluta - In Dematerializzazione", true),
    RECRN003B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Consegnato presso Punti di Giacenza - In Dematerializzazione", true),
    RECRN004B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Mancata consegna presso Punti di Giacenza - In Dematerializzazione", true),
    RECRN005B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Compiuta giacenza - In Dematerializzazione", true),
    RECRN010(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Inesito", false),
    PNRN012(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Perfezionamento per decorrenza termini", false),

    // RIR
    RECRI001(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.RIR, EventStatus.PROGRESS, List.of(), "Avviato all’estero", false),
    RECRI002(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.RIR, EventStatus.PROGRESS, List.of(), "Ingresso nel paese estero", false),
    RECRI005(EventTypeEnum.RETRYABLE_EVENT, ProductType.RIR, EventStatus.PROGRESS, List.of(F01, F02, F03, F04), "Furto, smarrimento o deterioramento", false),
    RECRI003A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.RIR, EventStatus.PROGRESS, List.of(), "Consegnato - pre-esito", false),
    RECRI004A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.RIR, EventStatus.PROGRESS, List.of(), "Mancata consegna - pre-esito", false),
    RECRI003B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.RIR, EventStatus.PROGRESS, List.of(), "Consegnato - In Dematerializzazione", true),
    RECRI004B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.RIR, EventStatus.PROGRESS, List.of(), "Mancata consegna - In Dematerializzazione", true),
    RECRI003C(EventTypeEnum.FINAL_EVENT, ProductType.RIR, EventStatus.OK, List.of(),"Consegnato - Fascicolo Chiuso", false),
    RECRI004C(EventTypeEnum.FINAL_EVENT, ProductType.RIR, EventStatus.KO, List.of(), "Mancata consegna - Fascicolo Chiuso", false),

    // CON
    CON998(EventTypeEnum.NOT_RETRYABLE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Scartato NODOC", false),
    CON997(EventTypeEnum.NOT_RETRYABLE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Scartato CAP/INTERNAZIONALE", false),
    CON996(EventTypeEnum.NOT_RETRYABLE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Scartato PDF", false),
    CON995(EventTypeEnum.NOT_RETRYABLE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Errore stampa", false),
    CON993(EventTypeEnum.NOT_RETRYABLE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Errore Stampa (parziale)", false),
    CON080(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Stampato ed Imbustato", false),
    CON020(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Affido conservato", false),
    CON010(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Distinta Elettronica inviata a Recapitista", false),
    CON011(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Distinta Elettronica Sigillata", false),
    CON012(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "OK Distinta Elettronica da Recapitista", false),
    CON09A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Materialità Pronta", false),
    CON016(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "PICKUP Sigillata", false),
    CON018(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Accettazione Recapitista", false);
    //eventi non gestiti da paper-channel ma previsti dal consolidatore
    //CON992(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "KO Distinta Elettronica da Recapitista", false),
    //CON991(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.ALL, EventStatus.PROGRESS, List.of(), "Mancata Accetazione Recapitsita ", false);

    private final EventTypeEnum codeType;
    private final ProductType productType;
    private final EventStatus status;
    private final List<DeliveryFailureCauseEnum> deliveryFailureCauseList;
    private final String statusCodeDescription;
    private final boolean isFinalDemat;

    EventStatusCodeEnum(EventTypeEnum codeType, ProductType productType,
                                EventStatus status, List<DeliveryFailureCauseEnum> deliveryFailureCauseList, String statusCodeDescription, boolean isFinalDemat) {
        this.codeType = codeType;
        this.productType = productType;
        this.status = status;
        this.statusCodeDescription = statusCodeDescription;
        this.deliveryFailureCauseList = deliveryFailureCauseList;
        this.isFinalDemat = isFinalDemat;
    }

    public static EventStatusCodeEnum fromKey(String key) {
        return Stream.of(values())
                .filter(definition -> definition.name().equals(key))
                .findFirst()
                .orElse(null);
    }
}