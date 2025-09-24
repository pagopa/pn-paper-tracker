package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
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
                             StateUpdater stateUpdater,
                             CheckTrackingState checkTrackingState,
                             CheckOcrResponse checkOcrResponse) {
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
                checkTrackingState,
                checkOcrResponse);
    }

    @Override
    public ProductType getProductType() { return ProductType.AR; }
}