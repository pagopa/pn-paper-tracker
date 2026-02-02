package it.pagopa.pn.papertracker.service.handler_step.RIS;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.service.handler_step.generic.*;
import org.springframework.stereotype.Component;

@Component
public class HandlersFactoryRis extends AbstractHandlersFactory {

    public HandlersFactoryRis(MetadataUpserter metadataUpserter,
                              DeliveryPushSender deliveryPushSender,
                              FinalEventBuilderRis finalEventBuilder,
                              IntermediateEventsBuilder intermediateEventsBuilder,
                              DematValidatorRis dematValidator,
                              SequenceValidatorRis sequenceValidator,
                              RetrySender retrySender,
                              NotRetryableErrorInserting notRetryableErrorInserting,
                              DuplicatedEventFiltering duplicatedEventFiltering,
                              CheckTrackingState checkTrackingState,
                              CheckOcrResponse checkOcrResponse,
                              RetrySenderCON996 retrySenderCON996) {
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
                retrySenderCON996);
    }

    @Override
    public ProductType getProductType() { return ProductType.RIS; }
}
