package it.pagopa.pn.papertracker.config;

import com.sngular.apigenerator.asyncapi.business_model.model.event.ExternalChannelOutputsPayload;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.ExternalChannelCodeTypeEnum;
import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class StatusCodeConfiguration {

    @Getter
    public enum StatusCodeConfigurationEnum {
        RECRN006(ExternalChannelCodeTypeEnum.FINAL_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Furto/Smarrimento/deterioramento"),
        RECRN013(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Non Rendicontabile "),
        RECRN015(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Causa Forza Maggiore"),
        RECRN001C(ExternalChannelCodeTypeEnum.FINAL_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.OK, "Consegnato - Fascicolo Chiuso"),
        RECRN002C(ExternalChannelCodeTypeEnum.FINAL_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.OK, "Mancata consegna - Fascicolo Chiuso"),
        RECRN002F(ExternalChannelCodeTypeEnum.FINAL_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.KO, "Irreperibilità Assoluta - Fascicolo Chiuso"),
        RECRN003C(ExternalChannelCodeTypeEnum.FINAL_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.OK, "Consegnato presso Punti di Giacenza - Fascicolo Chiuso"),
        RECRN004C(ExternalChannelCodeTypeEnum.FINAL_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.OK, "Mancata consegna presso Punti di Giacenza - Fascicolo Chiuso"),
        RECRN005C(ExternalChannelCodeTypeEnum.FINAL_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.OK, "Compiuta giacenza - Fascicolo Chiuso"),
        RECRN011(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "In giacenza"),
        RECRN001A(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Consegnato - pre-esito"),
        RECRN002A(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Mancata consegna - pre-esito"),
        RECRN002D(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Irreperibilità Assoluta - pre-esito"),
        RECRN003A(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Consegnato presso Punti di Giacenza - pre-esito"),
        RECRN004A(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Mancata consegna presso Punti di Giacenza - pre-esito"),
        RECRN005A(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Compiuta giacenza pre-esito"),
        RECRN001B(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Consegnato - In Dematerializzazione"),
        RECRN002B(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Mancata consegna - In Dematerializzazione"),
        RECRN002E(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Irreperibilità Assoluta - In Dematerializzazione"),
        RECRN003B(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Consegnato presso Punti di Giacenza - In Dematerializzazione"),
        RECRN004B(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Mancata consegna presso Punti di Giacenza - In Dematerializzazione"),
        RECRN005B(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Compiuta giacenza - In Dematerializzazione"),
        RECRN010(ExternalChannelCodeTypeEnum.INTERMEDIATE_STATUS, ProductType.AR, ExternalChannelOutputsPayload.StatusCode.PROGRESS, "Inesito");

        private final ExternalChannelCodeTypeEnum codeType;
        private final ProductType productType;
        private final ExternalChannelOutputsPayload.StatusCode status;
        private final String statusCodeDescription;

        StatusCodeConfigurationEnum(ExternalChannelCodeTypeEnum codeType, ProductType productType,
                                    ExternalChannelOutputsPayload.StatusCode status, String statusCodeDescription) {
            this.codeType = codeType;
            this.productType = productType;
            this.status = status;
            this.statusCodeDescription = statusCodeDescription;
        }

        public static StatusCodeConfigurationEnum fromKey(String key) {
            return Stream.of(values())
                    .filter(definition -> definition.name().equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No sequence found for key: " + key));
        }
    }

    @Bean
    public Map<String, ExternalChannelCodeTypeEnum> statusCodesWithCodeTypeMap() {
        return Stream.of(StatusCodeConfigurationEnum.values())
                .collect(Collectors.toMap(
                        StatusCodeConfigurationEnum::name,
                        StatusCodeConfigurationEnum::getCodeType
                ));
    }

    public ExternalChannelOutputsPayload.StatusCode getStatusFromStatusCode(String statusCode) {
        return Stream.of(StatusCodeConfigurationEnum.values())
                .filter(statusCodeEnum -> statusCodeEnum.name().equals(statusCode))
                .findFirst()
                .map(StatusCodeConfigurationEnum::getStatus)
                .orElseThrow(() -> new IllegalArgumentException("No status found for status code: " + statusCode));
    }
}