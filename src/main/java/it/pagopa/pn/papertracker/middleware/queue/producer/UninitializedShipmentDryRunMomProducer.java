package it.pagopa.pn.papertracker.middleware.queue.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.api.dto.events.AbstractSqsMomProducer;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;

@Slf4j
public class UninitializedShipmentDryRunMomProducer extends AbstractSqsMomProducer<ExternalChannelEvent>  {

    public UninitializedShipmentDryRunMomProducer(SqsClient sqsClient, String topic, ObjectMapper objectMapper, Class<ExternalChannelEvent> msgClass) {
        super(sqsClient, topic, objectMapper, msgClass);
    }

}