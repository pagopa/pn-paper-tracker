package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import it.pagopa.pn.papertracker.exception.PaperTrackerExceptionHandler;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.AR.HandlersFactoryAr;
import it.pagopa.pn.papertracker.service.handler_step.RIR.HandlersFactoryRir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static it.pagopa.pn.papertracker.config.StatusCodeConfiguration.StatusCodeConfigurationEnum.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExternalChannelHandlerTest {

    @Mock
    private HandlersFactoryAr handlersFactoryAr;

    @Mock
    private HandlersFactoryRir handlersFactoryRir;

    @Mock
    private PaperTrackerExceptionHandler paperTrackerExceptionHandler;

    private ExternalChannelHandler externalChannelHandler;

    @BeforeEach
    void setUp() {
        externalChannelHandler = new ExternalChannelHandler(paperTrackerExceptionHandler, handlersFactoryAr, handlersFactoryRir);
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_PROGRESS() {
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN002A.name());
        when(handlersFactoryAr.buildIntermediateEventsHandler(any(HandlerContext.class))).thenReturn(Mono.empty());
        externalChannelHandler.handleExternalChannelMessage(payload);
        verify(handlersFactoryAr).buildIntermediateEventsHandler(any(HandlerContext.class));
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_KO() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN006.name());

        when(handlersFactoryAr.buildRetryEventHandler(any(HandlerContext.class))).thenReturn(Mono.empty());

        externalChannelHandler.handleExternalChannelMessage(payload);

        verify(handlersFactoryAr, times(1)).buildRetryEventHandler(any(HandlerContext.class));
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_OK() {
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN004C.name());
        when(handlersFactoryAr.buildFinalEventsHandler(any(HandlerContext.class))).thenReturn(Mono.empty());
        externalChannelHandler.handleExternalChannelMessage(payload);
        verify(handlersFactoryAr).buildFinalEventsHandler(any(HandlerContext.class));
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_UNRECOGNIZED() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate("UNRECOGNIZED_STATUS");
        when(handlersFactoryAr.buildUnrecognizedEventsHandler(any(HandlerContext.class))).thenReturn(Mono.empty());
        externalChannelHandler.handleExternalChannelMessage(payload);
        verify(handlersFactoryAr).buildUnrecognizedEventsHandler(any(HandlerContext.class));
    }

    private SingleStatusUpdate getSingleStatusUpdate(String statusCode) {
        SingleStatusUpdate singleStatusUpdate = new SingleStatusUpdate();
        PaperProgressStatusEvent analogMail = new PaperProgressStatusEvent();
        analogMail.setRequestId("test-request-id");
        analogMail.setStatusCode(statusCode);
        singleStatusUpdate.setAnalogMail(analogMail);
        return singleStatusUpdate;
    }
}
