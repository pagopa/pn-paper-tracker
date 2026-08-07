package it.pagopa.pn.papertracker.middleware.queue.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.api.dto.events.AbstractSqsMomProducer;
import it.pagopa.pn.papertracker.middleware.queue.model.PaperTrackerErrorEvent;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;

@Slf4j
public class PaperTrackerErrorsMomProducer extends AbstractSqsMomProducer<PaperTrackerErrorEvent> {

    public PaperTrackerErrorsMomProducer(SqsClient sqsClient, String topic, ObjectMapper objectMapper, Class<PaperTrackerErrorEvent> msgClass) {
        super(sqsClient, topic, objectMapper, msgClass);
    }

}
