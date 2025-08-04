package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.AR.HandlersFactoryAr;
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

    private ExternalChannelHandler externalChannelHandler;

    @BeforeEach
    void setUp() {
        externalChannelHandler = new ExternalChannelHandler(new StatusCodeConfiguration(), handlersFactoryAr);
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_PROGRESS() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN002A.name());
        HandlerContext context = new HandlerContext();
        context.setPaperProgressStatusEvent(payload.getAnalogMail());

        when(handlersFactoryAr.buildIntermediateEventsHandler(context)).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload);

        //Assert
        verify(handlersFactoryAr).buildIntermediateEventsHandler(context);
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_KO() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN002F.name());
        HandlerContext context = new HandlerContext();
        context.setPaperProgressStatusEvent(payload.getAnalogMail());

        when(handlersFactoryAr.buildRetryEventHandler(context)).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload);

        //Assert
        verify(handlersFactoryAr).buildRetryEventHandler(context);
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_OK() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN004C.name());
        HandlerContext context = new HandlerContext();
        context.setPaperProgressStatusEvent(payload.getAnalogMail());

        when(handlersFactoryAr.buildFinalEventsHandler(context)).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload);

        //Assert
        verify(handlersFactoryAr).buildFinalEventsHandler(context);
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_UNRECOGNIZED() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate("UNRECOGNIZED_STATUS");
        HandlerContext context = new HandlerContext();
        context.setPaperProgressStatusEvent(payload.getAnalogMail());

        when(handlersFactoryAr.buildUnrecognizedEventsHandler(context)).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload);

        //Assert
        verify(handlersFactoryAr).buildUnrecognizedEventsHandler(context);
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
