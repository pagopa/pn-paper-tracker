package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.service.handler_step.AR.FinalEventBuilderAr;
import it.pagopa.pn.papertracker.service.handler_step.AR.SequenceValidatorAr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HandlersFactoryGeneric extends AbstractHandlersFactory {

    public HandlersFactoryGeneric(MetadataUpserter metadataUpserter,
                                  DeliveryPushSender deliveryPushSender,
                                  FinalEventBuilderAr finalEventBuilder,
                                  IntermediateEventsBuilder intermediateEventsBuilder,
                                  DematValidator dematValidator,
                                  SequenceValidatorAr sequenceValidator,
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