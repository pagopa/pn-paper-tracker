package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum;
import it.pagopa.pn.papertracker.model.EventStatus;
import it.pagopa.pn.papertracker.model.EventTypeEnum;
import lombok.Getter;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Stream;

import static it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum.*;

@Configuration
public class StatusCodeConfiguration {

    @Getter
    public enum StatusCodeConfigurationEnum {
        // AR
        RECRN006(EventTypeEnum.RETRYABLE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Furto/Smarrimento/deterioramento", false),
        RECRN013(EventTypeEnum.RETRYABLE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Non Rendicontabile ", false),
        RECRN015(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Causa Forza Maggiore", false),
        RECRN001C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Consegnato - Fascicolo Chiuso", false),
        RECRN002C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Mancata consegna - Fascicolo Chiuso", false),
        RECRN002F(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.KO, List.of(), "Irreperibilità Assoluta - Fascicolo Chiuso", false),
        RECRN003C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Consegnato presso Punti di Giacenza - Fascicolo Chiuso", false),
        RECRN004C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Mancata consegna presso Punti di Giacenza - Fascicolo Chiuso", false),
        RECRN005C(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Compiuta giacenza - Fascicolo Chiuso", false),
        RECRN011(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "In giacenza", false),
        RECRN001A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Consegnato - pre-esito", false),
        RECRN002A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Mancata consegna - pre-esito", false),
        RECRN002D(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Irreperibilità Assoluta - pre-esito", false),
        RECRN003A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Consegnato presso Punti di Giacenza - pre-esito", false),
        RECRN004A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Mancata consegna presso Punti di Giacenza - pre-esito", false),
        RECRN005A(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Compiuta giacenza pre-esito", false),
        RECRN001B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Consegnato - In Dematerializzazione", true),
        RECRN002B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(M02, M05, M06, M07, M08, M09), "Mancata consegna - In Dematerializzazione", true),
        RECRN002E(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(M01, M03, M04), "Irreperibilità Assoluta - In Dematerializzazione", true),
        RECRN003B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Consegnato presso Punti di Giacenza - In Dematerializzazione", true),
        RECRN004B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Mancata consegna presso Punti di Giacenza - In Dematerializzazione", true),
        RECRN005B(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Compiuta giacenza - In Dematerializzazione", true),
        RECRN010(EventTypeEnum.INTERMEDIATE_EVENT, ProductType.AR, EventStatus.PROGRESS, List.of(), "Inesito", false),
        PNRN012(EventTypeEnum.FINAL_EVENT, ProductType.AR, EventStatus.OK, List.of(),"Perfezionamento per decorrenza termini", false);

        private final EventTypeEnum codeType;
        private final ProductType productType;
        private final EventStatus status;
        private final List<DeliveryFailureCauseEnum> deliveryFailureCauseList;
        private final String statusCodeDescription;
        private final boolean isFinalDemat;

        StatusCodeConfigurationEnum(EventTypeEnum codeType, ProductType productType,
                                    EventStatus status, List<DeliveryFailureCauseEnum> deliveryFailureCauseList, String statusCodeDescription, boolean isFinalDemat) {
            this.codeType = codeType;
            this.productType = productType;
            this.status = status;
            this.statusCodeDescription = statusCodeDescription;
            this.deliveryFailureCauseList = deliveryFailureCauseList;
            this.isFinalDemat = isFinalDemat;
        }

        public static StatusCodeConfigurationEnum fromKey(String key) {
            return Stream.of(values())
                    .filter(definition -> definition.name().equals(key))
                    .findFirst()
                    .orElse(null);
        }
    }
}