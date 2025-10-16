package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.service.handler_step._890.RECAG012AEventBuilder;
import it.pagopa.pn.papertracker.service.handler_step._890.RECAG012EventChecker;
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
                             CheckTrackingState checkTrackingState,
                             CheckOcrResponse checkOcrResponse,
                             RetrySenderCON996 retrySenderCON996,
                             RECAG012EventChecker recag012EventChecker,
                             RECAG012AEventBuilder recag012AEventBuilder) {
        super(metadataUpserter,
                deliveryPushSender,
                finalEventBuilder,
                intermediateEventsBuilder,
                dematValidator,
                sequenceValidator,
                retrySender,
                notRetryableErrorInserting,
                duplicatedEventFiltering,
                checkTrackingState,
                checkOcrResponse,
                retrySenderCON996,
                recag012EventChecker,
                recag012AEventBuilder);
    }

    @Override
    public ProductType getProductType() { return ProductType.AR; }
}