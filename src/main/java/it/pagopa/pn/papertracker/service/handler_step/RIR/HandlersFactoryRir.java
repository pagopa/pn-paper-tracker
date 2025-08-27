package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.service.handler_step.generic.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HandlersFactoryRir extends AbstractHandlersFactory {

    public HandlersFactoryRir(MetadataUpserter metadataUpserter, DeliveryPushSender deliveryPushSender, GenericFinalEventBuilder finalEventBuilder, IntermediateEventsBuilder intermediateEventsBuilder, DematValidator dematValidator, GenericSequenceValidator sequenceValidator, RetrySender retrySender, DuplicatedEventFiltering duplicatedEventFiltering, StateUpdater stateUpdater) {
        super(metadataUpserter, deliveryPushSender, finalEventBuilder, intermediateEventsBuilder, dematValidator, sequenceValidator, retrySender, duplicatedEventFiltering, stateUpdater);
    }
}
