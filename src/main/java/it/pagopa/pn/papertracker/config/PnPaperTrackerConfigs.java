package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConfigurationProperties(prefix = "pn.paper-tracker")
@Data
@Import({SharedAutoConfiguration.class})
public class PnPaperTrackerConfigs {

    private Dao dao;


    @Data
    public static class Dao {

    }

    @Data
    public static class Topics{
        private String ocrOutputTopic;
    }
}
