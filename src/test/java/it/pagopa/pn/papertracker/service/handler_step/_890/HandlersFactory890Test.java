package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.EventTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.Handler;
import it.pagopa.pn.papertracker.service.handler_step.HandlerImpl;
import it.pagopa.pn.papertracker.service.handler_step.generic.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class HandlersFactory890Test {

    @Mock
    private MetadataUpserter metadataUpserter;

    @Mock
    private DeliveryPushSender deliveryPushSender;

    @Mock
    private FinalEventBuilder890 finalEventBuilder;

    @Mock
    private IntermediateEventsBuilder intermediateEventsBuilder;

    @Mock
    private DematValidator dematValidator;

    @Mock
    private SequenceValidator890 sequenceValidator;

    @Mock
    private RetrySender retrySender;

    @Mock
    private NotRetryableErrorInserting notRetryableErrorInserting;

    @Mock
    private DuplicatedEventFiltering duplicatedEventFiltering;

    @Mock
    private CheckTrackingState checkTrackingState;

    @Mock
    private CheckOcrResponse checkOcrResponse;

    @Mock
    private RetrySenderCON996 retrySenderCON996;

    @Mock
    private RECAG012EventChecker recag012EventChecker;

    @Mock
    private RECAG012EventBuilder recag012EventBuilder;

    @Mock
    private PendingFinalEventTrigger pendingFinalEventTrigger;

    private HandlersFactory890 handlersFactory;

    @BeforeEach
    void setUp() {
        handlersFactory = new HandlersFactory890(
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
                retrySenderCON996,
                recag012EventChecker,
                recag012EventBuilder,
                pendingFinalEventTrigger
        );
    }

    @Test
    void getProductTypeReturnsCorrectProductType() {
        assertEquals(ProductType._890, handlersFactory.getProductType());
    }

    @Test
    void getDispatcherReturnsStockIntermediateEventHandler() {
        // Arrange & Act
        Function<HandlerContext, Handler> dispatcher = handlersFactory.getDispatcher(EventTypeEnum.STOCK_INTERMEDIATE_EVENT);
        HandlerContext context = mock(HandlerContext.class);
        Handler handler = dispatcher.apply(context);

        // Assert
        assertNotNull(handler);
        assertInstanceOf(HandlerImpl.class, handler);
    }

    @Test
    void getDispatcherReturnsRecag012EventHandler() {
        // Arrange & Act
        Function<HandlerContext, Handler> dispatcher = handlersFactory.getDispatcher(EventTypeEnum.RECAG012_EVENT);
        HandlerContext context = mock(HandlerContext.class);
        Handler handler = dispatcher.apply(context);

        // Assert
        assertNotNull(handler);
        assertInstanceOf(HandlerImpl.class, handler);
    }

}