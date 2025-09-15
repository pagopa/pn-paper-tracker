package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.service.handler_step.generic.NotRetryableErrorInserting;
import it.pagopa.pn.papertracker.service.handler_step.generic.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HandlersFactoryRir extends AbstractHandlersFactory {

    public HandlersFactoryRir(MetadataUpserter metadataUpserter,
                              DeliveryPushSender deliveryPushSender,
                              FinalEventBuilderRir finalEventBuilder,
                              IntermediateEventsBuilder intermediateEventsBuilder,
                              DematValidator dematValidator,
                              SequenceValidatorRir sequenceValidator,
                              RetrySender retrySender,
                              NotRetryableErrorInserting notRetryableErrorInserting,
                              DuplicatedEventFiltering duplicatedEventFiltering,
                              StateUpdater stateUpdater,
                              CheckTrackingState checkTrackingState) {
        super(metadataUpserter,
                deliveryPushSender,
                finalEventBuilder,
                intermediateEventsBuilder,
                dematValidator,
                sequenceValidator,
                retrySender,
                notRetryableErrorInserting,
                duplicatedEventFiltering,
                stateUpdater,
                checkTrackingState);
    }
}
