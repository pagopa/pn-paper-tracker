package it.pagopa.pn.papertracker.middleware.queue.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.api.dto.events.AbstractSqsMomProducer;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelOutputEvent;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;

@Slf4j
public class ExternalChannelOutputsMomProducer extends AbstractSqsMomProducer<ExternalChannelOutputEvent>  {

    public ExternalChannelOutputsMomProducer(SqsClient sqsClient, String topic, String url, ObjectMapper objectMapper, Class<ExternalChannelOutputEvent> msgClass) {
        super(sqsClient, topic, url, objectMapper, msgClass);
    }

}