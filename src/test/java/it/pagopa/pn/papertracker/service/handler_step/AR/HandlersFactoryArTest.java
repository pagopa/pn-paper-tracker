package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.DeliveryPushSender;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.MetadataUpserter;
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


//TODO implementare junits per gestione evento finale
@ExtendWith(MockitoExtension.class)
class HandlersFactoryArTest {

    @Mock
    private MetadataUpserter metadataUpserter;

    @Mock
    private DeliveryPushSender deliveryPushSender;

    @Mock
    private HandlerStep mockHandlerStep1;

    @Mock
    private HandlerStep mockHandlerStep2;

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

        when(mockHandlerStep1.execute(handlerContext)).thenReturn(Mono.empty());
        when(mockHandlerStep2.execute(handlerContext)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(handlersFactoryAr.buildEventsHandler(steps, handlerContext))
                .verifyComplete();

        // Verify execution order
        InOrder inOrder = inOrder(mockHandlerStep1, mockHandlerStep2);
        inOrder.verify(mockHandlerStep1).execute(handlerContext);
        inOrder.verify(mockHandlerStep2).execute(handlerContext);
    }
}