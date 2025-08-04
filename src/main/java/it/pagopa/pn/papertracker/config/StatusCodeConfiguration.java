package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.EventStatus;
import it.pagopa.pn.papertracker.model.EventStatusCodeTypeEnum;
import it.pagopa.pn.papertracker.model.ExternalChannelCodeTypeEnum;
import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class StatusCodeConfiguration {

    @Getter
    public enum StatusCodeConfigurationEnum {
        RECRN006(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.PROGRESS, "Furto/Smarrimento/deterioramento"),
        RECRN013(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Non Rendicontabile "),
        RECRN015(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Causa Forza Maggiore"),
        RECRN001C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Consegnato - Fascicolo Chiuso"),
        RECRN002C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Mancata consegna - Fascicolo Chiuso"),
        RECRN002F(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.KO, "Irreperibilità Assoluta - Fascicolo Chiuso"),
        RECRN003C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Consegnato presso Punti di Giacenza - Fascicolo Chiuso"),
        RECRN004C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Mancata consegna presso Punti di Giacenza - Fascicolo Chiuso"),
        RECRN005C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Compiuta giacenza - Fascicolo Chiuso"),
        RECRN011(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "In giacenza"),
        RECRN001A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Consegnato - pre-esito"),
        RECRN002A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Mancata consegna - pre-esito"),
        RECRN002D(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Irreperibilità Assoluta - pre-esito"),
        RECRN003A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Consegnato presso Punti di Giacenza - pre-esito"),
        RECRN004A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Mancata consegna presso Punti di Giacenza - pre-esito"),
        RECRN005A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Compiuta giacenza pre-esito"),
        RECRN001B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Consegnato - In Dematerializzazione"),
        RECRN002B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Mancata consegna - In Dematerializzazione"),
        RECRN002E(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Irreperibilità Assoluta - In Dematerializzazione"),
        RECRN003B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Consegnato presso Punti di Giacenza - In Dematerializzazione"),
        RECRN004B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Mancata consegna presso Punti di Giacenza - In Dematerializzazione"),
        RECRN005B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Compiuta giacenza - In Dematerializzazione"),
        RECRN010(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Inesito"),
        PNRN012(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Perfezionamento per decorrenza termini");

        private final EventStatusCodeTypeEnum codeType;
        private final ProductType productType;
        private final EventStatus status;
        private final String statusCodeDescription;

        StatusCodeConfigurationEnum(EventStatusCodeTypeEnum codeType, ProductType productType,
                                    EventStatus status, String statusCodeDescription) {
            this.codeType = codeType;
            this.productType = productType;
            this.status = status;
            this.statusCodeDescription = statusCodeDescription;
        }

        public static StatusCodeConfigurationEnum fromKey(String key) {
            return Stream.of(values())
                    .filter(definition -> definition.name().equals(key))
                    .findFirst()
                    .orElse(null);
        }
    }

    @Bean
    public Map<String, EventStatusCodeTypeEnum> statusCodesWithCodeTypeMap() {
        return Stream.of(StatusCodeConfigurationEnum.values())
                .collect(Collectors.toMap(
                        StatusCodeConfigurationEnum::name,
                        StatusCodeConfigurationEnum::getCodeType
                ));
    }

    public EventStatus getStatusFromStatusCode(String statusCode) {
        return Stream.of(StatusCodeConfigurationEnum.values())
                .filter(statusCodeEnum -> statusCodeEnum.name().equals(statusCode))
                .findFirst()
                .map(StatusCodeConfigurationEnum::getStatus)
                .orElseThrow(() -> new IllegalArgumentException("No status found for status code: " + statusCode));
    }
}