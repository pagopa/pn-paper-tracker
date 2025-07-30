package it.pagopa.pn.papertracker.middleware.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.queue.model.DeliveryPushEvent;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.SqsClient;

@ActiveProfiles("local")
@Configuration
@Slf4j
public class PnPaperTrackerMiddlewareConfigs {

    @Qualifier("pnPaperTrackerConfigs")
    @Autowired
    private PnPaperTrackerConfigs pnPaperChannelConfigs;


    @Bean
    public OcrMomProducer ocrMomProducer(SqsClient sqsClient, ObjectMapper objMapper) {
        return new OcrMomProducer(sqsClient, this.pnPaperChannelConfigs.getQueueOcrInput(), objMapper, OcrEvent.class);
    }

    @Bean
    public ExternalChannelOutputsMomProducer externalChannelOutputsMomProducer(SqsClient sqsClient, ObjectMapper objMapper) {
        return new ExternalChannelOutputsMomProducer(sqsClient, this.pnPaperChannelConfigs.getExternalChannelOutputsQueue(), this.pnPaperChannelConfigs.getExternalChannelOutputsQueueUrl(), objMapper, DeliveryPushEvent.class);
    }
}

