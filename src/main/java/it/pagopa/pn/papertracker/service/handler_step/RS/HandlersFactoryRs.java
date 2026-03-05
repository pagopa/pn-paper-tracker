package it.pagopa.pn.papertracker.service.handler_step.RS;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.service.handler_step.generic.*;
import org.springframework.stereotype.Component;

@Component
public class HandlersFactoryRs extends AbstractHandlersFactory {

    public HandlersFactoryRs(MetadataUpserter metadataUpserter,
                             CheckTrackingProduct checkTrackingProduct,
                             DeliveryPushSender deliveryPushSender,
                             FinalEventBuilderRs finalEventBuilder,
                             IntermediateEventsBuilder intermediateEventsBuilder,
                             DematValidatorRs dematValidator,
                             SequenceValidatorRs sequenceValidator,
                             RetrySender retrySender,
                             NotRetryableErrorInserting notRetryableErrorInserting,
                             DuplicatedEventFiltering duplicatedEventFiltering,
                             CheckTrackingState checkTrackingState,
                             CheckOcrResponse checkOcrResponse,
                             RetrySenderCON996 retrySenderCON996) {
        super(metadataUpserter,
                checkTrackingProduct,
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
                retrySenderCON996);
    }

    @Override
    public ProductType getProductType() { return ProductType.RS; }
}
