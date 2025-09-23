package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.model.EventTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.*;
import it.pagopa.pn.papertracker.service.handler_step.generic.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class HandlersFactoryRirTest {

    @Mock
    private MetadataUpserter metadataUpserter;

    @Mock
    private SequenceValidatorRir sequenceValidatorRir;

    @Mock
    private DematValidator dematValidator;

    @Mock
    private FinalEventBuilderRir finalEventBuilder;

    @Mock
    private StateUpdater stateUpdater;

    @Mock
    private DuplicatedEventFiltering duplicatedEventFiltering;

    @Mock
    private RetrySender retrySender;

    @Mock
    private DeliveryPushSender deliveryPushSender;

    @Mock
    private IntermediateEventsBuilder intermediateEventsBuilder;

    @Mock
    private NotRetryableErrorInserting notRetryableErrorInserting;

    @Mock
    private HandlerStep mockHandlerStep1;

    @Mock
    private HandlerStep mockHandlerStep2;

    @Mock
    private CheckTrackingState checkTrackingState;

    @InjectMocks
    private HandlersFactoryRir handlersFactoryRir;

    private HandlerContext handlerContext;

    @BeforeEach
    void setUp() {
        handlerContext = new HandlerContext();
    }

    @Test
    void buildEventsHandler_WithValidSteps_ExecutesAllStepsInOrder() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(sequenceValidatorRir.execute(handlerContext)).thenReturn(Mono.empty());
        when(dematValidator.execute(handlerContext)).thenReturn(Mono.empty());
        when(finalEventBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(stateUpdater.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryRir.buildFinalEventsHandler(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter, sequenceValidatorRir, dematValidator, finalEventBuilder, deliveryPushSender, stateUpdater);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(sequenceValidatorRir).execute(handlerContext);
        inOrder.verify(dematValidator).execute(handlerContext);
        inOrder.verify(finalEventBuilder).execute(handlerContext);
        inOrder.verify(deliveryPushSender).execute(handlerContext);
        inOrder.verify(stateUpdater).execute(handlerContext);
    }


    @Test
    void buildEventsHandler_WhenSecondStepFails_FirstStepStillExecuted() {
        // Arrange
        RuntimeException testException = new RuntimeException("Second step failed");

        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.error(testException));

        // Act & Assert
        StepVerifier.create(handlersFactoryRir.handle(EventTypeEnum.INTERMEDIATE_EVENT, new HandlerContext()))
                .expectError(RuntimeException.class)
                .verify();

        InOrder inOrder = inOrder(metadataUpserter, checkTrackingState);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(checkTrackingState).execute(handlerContext);

    }

    @Test
    void buildFinalEventsHandler_ExecutesSuccessfully() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(sequenceValidatorRir.execute(handlerContext)).thenReturn(Mono.empty());
        when(dematValidator.execute(handlerContext)).thenReturn(Mono.empty());
        when(finalEventBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(stateUpdater.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryRir.buildFinalEventsHandler(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter, sequenceValidatorRir, dematValidator, finalEventBuilder, deliveryPushSender, stateUpdater);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(sequenceValidatorRir).execute(handlerContext);
        inOrder.verify(dematValidator).execute(handlerContext);
        inOrder.verify(finalEventBuilder).execute(handlerContext);
        inOrder.verify(deliveryPushSender).execute(handlerContext);
        inOrder.verify(stateUpdater).execute(handlerContext);
    }

    @Test
    void buildIntermediateEventsHandler_ExecutesMetadataUpserterAndDeliveryPushSender() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(intermediateEventsBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        when(duplicatedEventFiltering.execute(handlerContext)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(handlersFactoryRir.buildIntermediateEventsHandler(handlerContext))
                .verifyComplete();

        // Verify both steps were executed in the correct order
        InOrder inOrder = inOrder(metadataUpserter, intermediateEventsBuilder, deliveryPushSender);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(intermediateEventsBuilder).execute(handlerContext);
        inOrder.verify(deliveryPushSender).execute(handlerContext);
    }

    @Test
    void buildRetryEventHandler_ExecutesSuccessfully() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(retrySender.execute(handlerContext)).thenReturn(Mono.empty());
        when(stateUpdater.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(intermediateEventsBuilder.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryRir.buildRetryEventHandler(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter, retrySender);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(retrySender).execute(handlerContext);
    }

    @Test
    void buildNotRetryableEventHandler_ExecutesSuccessfully() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(duplicatedEventFiltering.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(intermediateEventsBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        when(notRetryableErrorInserting.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryRir.buildNotRetryableEventHandler(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter, duplicatedEventFiltering, notRetryableErrorInserting, intermediateEventsBuilder, deliveryPushSender);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(duplicatedEventFiltering).execute(handlerContext);
        inOrder.verify(notRetryableErrorInserting).execute(handlerContext);
        inOrder.verify(intermediateEventsBuilder).execute(handlerContext);
        inOrder.verify(deliveryPushSender).execute(handlerContext);
    }

    @Test
    void buildOcrResponseHandler() {
        // Arrange
        when(finalEventBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(stateUpdater.execute(handlerContext)).thenReturn(Mono.empty());
        // Act & Assert
        StepVerifier.create(handlersFactoryRir.buildOcrResponseHandler(handlerContext))
                .verifyComplete();
    }

    @Test
    void buildUnrecognizedEventsHandler_ExecutesMetadataUpserter() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryRir.buildUnrecognizedEventsHandler(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter);
        inOrder.verify(metadataUpserter).execute(handlerContext);
    }
}