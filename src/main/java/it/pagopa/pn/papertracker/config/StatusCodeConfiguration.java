package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.EventStatus;
import it.pagopa.pn.papertracker.model.EventStatusCodeTypeEnum;
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
        RECRN006(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.PROGRESS, "Furto/Smarrimento/deterioramento", false),
        RECRN013(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Non Rendicontabile ", false),
        RECRN015(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Causa Forza Maggiore", false),
        RECRN001C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Consegnato - Fascicolo Chiuso", false),
        RECRN002C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Mancata consegna - Fascicolo Chiuso", false),
        RECRN002F(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.KO, "Irreperibilità Assoluta - Fascicolo Chiuso", false),
        RECRN003C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Consegnato presso Punti di Giacenza - Fascicolo Chiuso", false),
        RECRN004C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Mancata consegna presso Punti di Giacenza - Fascicolo Chiuso", false),
        RECRN005C(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Compiuta giacenza - Fascicolo Chiuso", false),
        RECRN011(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "In giacenza", false),
        RECRN001A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Consegnato - pre-esito", false),
        RECRN002A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Mancata consegna - pre-esito", false),
        RECRN002D(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Irreperibilità Assoluta - pre-esito", false),
        RECRN003A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Consegnato presso Punti di Giacenza - pre-esito", false),
        RECRN004A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Mancata consegna presso Punti di Giacenza - pre-esito", false),
        RECRN005A(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Compiuta giacenza pre-esito", false),
        RECRN001B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Consegnato - In Dematerializzazione", true),
        RECRN002B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Mancata consegna - In Dematerializzazione", true),
        RECRN002E(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Irreperibilità Assoluta - In Dematerializzazione", true),
        RECRN003B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Consegnato presso Punti di Giacenza - In Dematerializzazione", true),
        RECRN004B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Mancata consegna presso Punti di Giacenza - In Dematerializzazione", true),
        RECRN005B(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Compiuta giacenza - In Dematerializzazione", true),
        RECRN010(EventStatusCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, EventStatus.PROGRESS, "Inesito", false),
        PNRN012(EventStatusCodeTypeEnum.FINAL_STATUS, ProductType.AR, EventStatus.OK, "Perfezionamento per decorrenza termini", false);

        private final EventStatusCodeTypeEnum codeType;
        private final ProductType productType;
        private final EventStatus status;
        private final String statusCodeDescription;
        private final boolean isFinalDemat;

        StatusCodeConfigurationEnum(EventStatusCodeTypeEnum codeType, ProductType productType,
                                    EventStatus status, String statusCodeDescription, boolean isFinalDemat) {
            this.codeType = codeType;
            this.productType = productType;
            this.status = status;
            this.statusCodeDescription = statusCodeDescription;
            this.isFinalDemat = isFinalDemat;
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