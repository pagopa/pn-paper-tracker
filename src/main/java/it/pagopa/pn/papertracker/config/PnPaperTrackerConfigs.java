package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "pn.paper-tracker")
@Data
@Import({SharedAutoConfiguration.class})
public class PnPaperTrackerConfigs {

    private Dao dao;
    private String queueOcrInput;
    private String paperChannelBaseUrl;
    private Duration paperTrackingsTtlDuration;
    private Topics topics;
    private boolean sendOutputToDeliveryPush;
    private boolean enableOcrValidation;
    private String queueOcrInputsUrl;
    private String QueueOcrInputsRegion;
    private String externalChannelOutputsQueue;
    private String externalChannelOutputsQueueUrl;

    @Data
    public static class Dao {
        private String paperTrackingsErrorsTable;
        private String paperTrackerDryRunOutputsTable;
        private String paperTrackingsTable;
    }

    @Data
    public static class Topics {
        private String externalChannelToPaperTracker;
    }

}
