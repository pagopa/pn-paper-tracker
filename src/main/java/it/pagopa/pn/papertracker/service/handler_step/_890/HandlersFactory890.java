package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.EventTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.Handler;
import it.pagopa.pn.papertracker.service.handler_step.HandlerImpl;
import it.pagopa.pn.papertracker.service.handler_step.generic.*;

import java.util.List;
import java.util.function.Function;

public class HandlersFactory890 extends AbstractHandlersFactory {

    private final RECAG012EventChecker recag012EventChecker;
    private final RECAG012AEventBuilder recag012AEventBuilder;

    public HandlersFactory890(
            MetadataUpserter metadataUpserter,
            DeliveryPushSender deliveryPushSender,
            GenericFinalEventBuilder finalEventBuilder,
            IntermediateEventsBuilder intermediateEventsBuilder,
            DematValidator dematValidator,
            GenericSequenceValidator sequenceValidator,
            RetrySender retrySender,
            NotRetryableErrorInserting notRetryableErrorInserting,
            DuplicatedEventFiltering duplicatedEventFiltering,
            CheckTrackingState checkTrackingState,
            CheckOcrResponse checkOcrResponse,
            RetrySenderCON996 retrySenderCON996,
            RECAG012EventChecker recag012EventChecker,
            RECAG012AEventBuilder recag012AEventBuilder
    ) {
        super(
                metadataUpserter,
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
                retrySenderCON996
        );
        this.recag012EventChecker = recag012EventChecker;
        this.recag012AEventBuilder = recag012AEventBuilder;
    }


    @Override
    public ProductType getProductType() {
        return ProductType._890;
    }

    @Override
    public Function<HandlerContext, Handler> getDispatcher(EventTypeEnum eventType) {
        return switch (eventType) {
            case STOCK_INTERMEDIATE_EVENT -> this::buildStockIntermediateEventHandler;
            case RECAG012_EVENT -> this::buildRecag012EventHandler;
            default -> super.getDispatcher(eventType);
        };
    }

    public Handler buildStockIntermediateEventHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        duplicatedEventFiltering,
                        recag012EventChecker,
                        intermediateEventsBuilder,
                        deliveryPushSender
                ));
    }

    public Handler buildRecag012EventHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        recag012EventChecker,
                        recag012AEventBuilder,
                        deliveryPushSender
                ));
    }
}
