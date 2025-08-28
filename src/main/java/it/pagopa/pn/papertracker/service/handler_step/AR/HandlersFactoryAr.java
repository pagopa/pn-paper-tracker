package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.service.handler_step.generic.NotRetryableErrorInserting;
import it.pagopa.pn.papertracker.service.handler_step.generic.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HandlersFactoryAr extends AbstractHandlersFactory {

    public HandlersFactoryAr(MetadataUpserter metadataUpserter,
                             DeliveryPushSender deliveryPushSender,
                             FinalEventBuilderAr finalEventBuilder,
                             IntermediateEventsBuilder intermediateEventsBuilder,
                             DematValidator dematValidator,
                             SequenceValidatorAr sequenceValidator,
                             RetrySender retrySender,
                             NotRetryableErrorInserting notRetryableErrorInserting,
                             DuplicatedEventFiltering duplicatedEventFiltering,
                             StateUpdater stateUpdater) {
        super(metadataUpserter,
                deliveryPushSender,
                finalEventBuilder,
                intermediateEventsBuilder,
                dematValidator,
                sequenceValidator,
                retrySender,
                notRetryableErrorInserting,
                duplicatedEventFiltering,
                stateUpdater);
    }
}