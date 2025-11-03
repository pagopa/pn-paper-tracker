package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.FileType;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "pn.paper-tracker")
@Data
@Import({SharedAutoConfiguration.class})
@Slf4j
public class PnPaperTrackerConfigs {

    private Dao dao;
    private String paperChannelBaseUrl;
    private String dataVaultBaseUrl;
    private String safeStorageBaseUrl;
    public String safeStorageCxId;
    private Duration paperTrackingsTtlDuration;
    private Duration paperTrackingsErrorsTtlDuration;
    private Topics topics;
    private int maxPcRetryMock;

    private List<ProductType> enableOcrValidationFor = new ArrayList<>();
    private List<FileType> enableOcrValidationForFile = new ArrayList<>();
    private List<String> saveAndNotSendToDeliveryPush = new ArrayList<>();

    private Duration compiutaGiacenzaArDuration;
    private boolean enableTruncatedDateForRefinementCheck;
    private Duration refinementDuration;

    @Data
    public static class Dao {
        private String paperTrackingsErrorsTable;
        private String paperTrackerDryRunOutputsTable;
        private String paperTrackingsTable;
    }

    @Data
    public static class Topics {
        // Consumer
        private String externalChannelToPaperTrackerQueue;
        private String pnOcrOutputsQueue;
        // Producer
        private String queueOcrInputsUrl;
        private String queueOcrInputsRegion;
        private String externalChannelOutputsQueue;
        private String uninitializedShipmentDryRunQueue;
        private String uninitializedShipmentRunQueue;
    }

    @PostConstruct
    public void init() {
        log.info("CONFIGURATIONS: {}", this);
    }

}
