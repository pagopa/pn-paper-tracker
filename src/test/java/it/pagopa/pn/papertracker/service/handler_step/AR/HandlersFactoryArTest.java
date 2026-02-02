package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.model.EventTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.Handler;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
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
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class HandlersFactoryArTest {

    @Mock
    private MetadataUpserter metadataUpserter;

    @Mock
    private SequenceValidatorAr sequenceValidatorAr;

    @Mock
    private DematValidatorAr dematValidator;

    @Mock
    private FinalEventBuilderAr finalEventBuilder;

    @Mock
    private DuplicatedEventFiltering duplicatedEventFiltering;

    @Mock
    private RetrySender retrySender;

    @Mock
    private DeliveryPushSender deliveryPushSender;

    @Mock
    private IntermediateEventsBuilder intermediateEventsBuilder;

    @Mock
    private HandlerStep mockHandlerStep1;

    @Mock
    private HandlerStep mockHandlerStep2;

    @Mock
    private NotRetryableErrorInserting notRetryableErrorInserting;

    @Mock
    private CheckTrackingState checkTrackingState;

    @Mock
    private CheckOcrResponse checkOcrResponse;

    @InjectMocks
    private HandlersFactoryAr handlersFactoryAr;

    private HandlerContext handlerContext;

    @BeforeEach
    void setUp() {
        handlerContext = new HandlerContext();
        // Add any necessary setup for HandlerContext if needed
    }

    @Test
    void buildEventsHandler_WithValidSteps_ExecutesAllStepsInOrder() {
        // Arrange
        List<HandlerStep> steps = Arrays.asList(mockHandlerStep1, mockHandlerStep2);

        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(duplicatedEventFiltering.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(intermediateEventsBuilder.execute(handlerContext)).thenReturn(Mono.empty());


        // Act & Assert
        StepVerifier.create(handlersFactoryAr.build(EventTypeEnum.INTERMEDIATE_EVENT, handlerContext).execute(handlerContext))
                .verifyComplete();

        // Verify execution order
        InOrder inOrder = inOrder(metadataUpserter, checkTrackingState,duplicatedEventFiltering, deliveryPushSender, intermediateEventsBuilder);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(checkTrackingState).execute(handlerContext);
        inOrder.verify(duplicatedEventFiltering).execute(handlerContext);
        inOrder.verify(deliveryPushSender).execute(handlerContext);
    }

    @Test
    void buildEventsHandler_WhenSecondStepFails_FirstStepStillExecuted() {
        // Arrange
        List<HandlerStep> steps = Arrays.asList(mockHandlerStep1, mockHandlerStep2);
        RuntimeException testException = new RuntimeException("Second step failed");

        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.error(testException));

        // Act & Assert
        StepVerifier.create(handlersFactoryAr.build(EventTypeEnum.INTERMEDIATE_EVENT, handlerContext).execute(handlerContext))
                .expectError(RuntimeException.class)
                .verify();

        // Verify both steps were attempted in order
        InOrder inOrder = inOrder(metadataUpserter, checkTrackingState);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(checkTrackingState).execute(handlerContext);
    }

    @Test
    void buildFinalEventsHandler_ExecutesSuccessfully() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(sequenceValidatorAr.execute(handlerContext)).thenReturn(Mono.empty());
        when(dematValidator.execute(handlerContext)).thenReturn(Mono.empty());
        when(finalEventBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryAr.buildFinalEventsHandler(handlerContext).execute(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter, checkTrackingState, sequenceValidatorAr, dematValidator, finalEventBuilder, deliveryPushSender);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(checkTrackingState).execute(handlerContext);
        inOrder.verify(sequenceValidatorAr).execute(handlerContext);
        inOrder.verify(dematValidator).execute(handlerContext);
        inOrder.verify(finalEventBuilder).execute(handlerContext);
        inOrder.verify(deliveryPushSender).execute(handlerContext);
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
        Handler handler = handlersFactoryAr.buildIntermediateEventsHandler(handlerContext);
        StepVerifier.create(handler.execute(handlerContext))
                .verifyComplete();

        // Verify both steps were executed in the correct order
        InOrder inOrder = inOrder(metadataUpserter, checkTrackingState, duplicatedEventFiltering, intermediateEventsBuilder, deliveryPushSender);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(checkTrackingState).execute(handlerContext);
        inOrder.verify(duplicatedEventFiltering).execute(handlerContext);
        inOrder.verify(intermediateEventsBuilder).execute(handlerContext);
        inOrder.verify(deliveryPushSender).execute(handlerContext);
    }

    @Test
    void buildRetryEventHandler_ExecutesSuccessfully() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(retrySender.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(intermediateEventsBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        // Act
        StepVerifier.create(handlersFactoryAr.buildRetryEventHandler(handlerContext).execute(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter, checkTrackingState, retrySender);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(checkTrackingState).execute(handlerContext);
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
        StepVerifier.create(handlersFactoryAr.buildNotRetryableEventHandler(handlerContext).execute(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter, checkTrackingState, duplicatedEventFiltering, notRetryableErrorInserting, intermediateEventsBuilder, deliveryPushSender);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(checkTrackingState).execute(handlerContext);
        inOrder.verify(duplicatedEventFiltering).execute(handlerContext);
        inOrder.verify(notRetryableErrorInserting).execute(handlerContext);
        inOrder.verify(intermediateEventsBuilder).execute(handlerContext);
        inOrder.verify(deliveryPushSender).execute(handlerContext);
    }

    @Test
    void buildOcrResponseHandler() {
        // Arrange
        when(checkOcrResponse.execute(handlerContext)).thenReturn(Mono.empty());
        when(finalEventBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        // Act & Assert
        StepVerifier.create(handlersFactoryAr.buildOcrResponseHandler(handlerContext).execute(handlerContext))
                .verifyComplete();
    }

    @Test
    void buildEventsHandler_WithSingleStep_ExecutesSuccessfully() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(duplicatedEventFiltering.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(intermediateEventsBuilder.execute(handlerContext)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(handlersFactoryAr.build(EventTypeEnum.INTERMEDIATE_EVENT, handlerContext).execute(handlerContext))
                .verifyComplete();
    }

    @Test
    void buildEventsHandler_VerifyContextPassedToAllSteps() {
        // Arrange

        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(checkTrackingState.execute(handlerContext)).thenReturn(Mono.empty());
        when(duplicatedEventFiltering.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(intermediateEventsBuilder.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryAr.build(EventTypeEnum.INTERMEDIATE_EVENT, handlerContext).execute(handlerContext))
                .verifyComplete();

        // Assert - verify the same context instance is passed to all steps
        InOrder inOrder = inOrder(metadataUpserter, checkTrackingState,duplicatedEventFiltering, deliveryPushSender, intermediateEventsBuilder);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(checkTrackingState).execute(handlerContext);
        inOrder.verify(duplicatedEventFiltering).execute(handlerContext);
        inOrder.verify(deliveryPushSender).execute(handlerContext);
    }

    @Test
    void buildSaveOnlyEventHandler_ExecutesMetadataUpserter() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryAr.buildSaveOnlyEventHandler(handlerContext).execute(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter);
        inOrder.verify(metadataUpserter).execute(handlerContext);
    }
}