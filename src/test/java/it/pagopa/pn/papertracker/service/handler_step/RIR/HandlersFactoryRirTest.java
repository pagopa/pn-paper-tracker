package it.pagopa.pn.papertracker.service.handler_step.RIR;

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
        List<HandlerStep> steps = Arrays.asList(mockHandlerStep1, mockHandlerStep2);

        when(mockHandlerStep1.execute(handlerContext)).thenReturn(Mono.empty());
        when(mockHandlerStep2.execute(handlerContext)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(handlersFactoryRir.buildEventsHandler(steps, handlerContext))
                .verifyComplete();

        // Verify execution order
        InOrder inOrder = inOrder(mockHandlerStep1, mockHandlerStep2);
        inOrder.verify(mockHandlerStep1).execute(handlerContext);
        inOrder.verify(mockHandlerStep2).execute(handlerContext);
    }

    @Test
    void buildEventsHandler_WithEmptyStepsList_CompletesSuccessfully() {
        // Arrange
        List<HandlerStep> emptySteps = Collections.emptyList();

        // Act & Assert
        StepVerifier.create(handlersFactoryRir.buildEventsHandler(emptySteps, handlerContext))
                .verifyComplete();

        // Verify no steps were executed
        verifyNoInteractions(mockHandlerStep1, mockHandlerStep2);
    }

    @Test
    void buildEventsHandler_WhenSecondStepFails_FirstStepStillExecuted() {
        // Arrange
        List<HandlerStep> steps = Arrays.asList(mockHandlerStep1, mockHandlerStep2);
        RuntimeException testException = new RuntimeException("Second step failed");

        when(mockHandlerStep1.execute(handlerContext)).thenReturn(Mono.empty());
        when(mockHandlerStep2.execute(handlerContext)).thenReturn(Mono.error(testException));

        // Act & Assert
        StepVerifier.create(handlersFactoryRir.buildEventsHandler(steps, handlerContext))
                .expectError(RuntimeException.class)
                .verify();

        // Verify both steps were attempted in order
        InOrder inOrder = inOrder(mockHandlerStep1, mockHandlerStep2);
        inOrder.verify(mockHandlerStep1).execute(handlerContext);
        inOrder.verify(mockHandlerStep2).execute(handlerContext);
    }

    @Test
    void buildFinalEventsHandler_ExecutesSuccessfully() {
        // Arrange
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(sequenceValidatorRir.execute(handlerContext)).thenReturn(Mono.empty());
        when(dematValidator.execute(handlerContext)).thenReturn(Mono.empty());
        when(finalEventBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(duplicatedEventFiltering.execute(handlerContext)).thenReturn(Mono.empty());
        when(stateUpdater.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryRir.buildFinalEventsHandler(handlerContext))
                .verifyComplete();

        // Assert
        InOrder inOrder = inOrder(metadataUpserter, duplicatedEventFiltering, sequenceValidatorRir, dematValidator, finalEventBuilder, deliveryPushSender, stateUpdater);
        inOrder.verify(metadataUpserter).execute(handlerContext);
        inOrder.verify(duplicatedEventFiltering).execute(handlerContext);
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
        when(retrySender.execute(handlerContext)).thenReturn(Mono.empty());
        when(duplicatedEventFiltering.execute(handlerContext)).thenReturn(Mono.empty());
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
        when(metadataUpserter.execute(handlerContext)).thenReturn(Mono.empty());
        when(finalEventBuilder.execute(handlerContext)).thenReturn(Mono.empty());
        when(deliveryPushSender.execute(handlerContext)).thenReturn(Mono.empty());
        when(stateUpdater.execute(handlerContext)).thenReturn(Mono.empty());
        // Act & Assert
        StepVerifier.create(handlersFactoryRir.buildOcrResponseHandler(handlerContext))
                .verifyComplete();
    }

    @Test
    void buildEventsHandler_WithSingleStep_ExecutesSuccessfully() {
        // Arrange
        List<HandlerStep> singleStep = Collections.singletonList(mockHandlerStep1);
        when(mockHandlerStep1.execute(handlerContext)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(handlersFactoryRir.buildEventsHandler(singleStep, handlerContext))
                .verifyComplete();

        verify(mockHandlerStep1).execute(handlerContext);
        verify(mockHandlerStep2, never()).execute(any());
    }

    @Test
    void buildEventsHandler_VerifyContextPassedToAllSteps() {
        // Arrange
        List<HandlerStep> steps = Arrays.asList(mockHandlerStep1, mockHandlerStep2);

        when(mockHandlerStep1.execute(handlerContext)).thenReturn(Mono.empty());
        when(mockHandlerStep2.execute(handlerContext)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(handlersFactoryRir.buildEventsHandler(steps, handlerContext))
                .verifyComplete();

        // Assert - verify the same context instance is passed to all steps
        verify(mockHandlerStep1).execute(handlerContext);
        verify(mockHandlerStep2).execute(handlerContext);
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