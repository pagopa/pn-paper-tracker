package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import it.pagopa.pn.papertracker.exception.PaperTrackerExceptionHandler;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.EventTypeEnum;
import it.pagopa.pn.papertracker.service.handler_step.generic.HandlersRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.HashMap;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.*;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExternalChannelHandlerTest {

    @Mock
    private HandlersRegistry handleRegistry;

    @Mock
    private PaperTrackerExceptionHandler paperTrackerExceptionHandler;

    private ExternalChannelHandler externalChannelHandler;

    private String eventId;

    @BeforeEach
    void setUp() {
        externalChannelHandler = new ExternalChannelHandler(
                                        paperTrackerExceptionHandler,
                                        handleRegistry);
        eventId = "eventId";
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_PROGRESS() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN002A.name());
        when(handleRegistry.handleEvent(eq(ProductType.AR),eq(EventTypeEnum.INTERMEDIATE_EVENT), any())).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload, getDryRunFlag(),null, eventId);

        //Assert
        verify(handleRegistry, times(1)).handleEvent(eq(ProductType.AR),eq(EventTypeEnum.INTERMEDIATE_EVENT), any());
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_KO() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN006.name());
        when(handleRegistry.handleEvent(eq(ProductType.AR),eq(EventTypeEnum.RETRYABLE_EVENT), any())).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload, getDryRunFlag(),null,  eventId);

        //Assert
        verify(handleRegistry, times(1)).handleEvent(eq(ProductType.AR),eq(EventTypeEnum.RETRYABLE_EVENT), any());
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_OK() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN004C.name());
        when(handleRegistry.handleEvent(eq(ProductType.AR),eq(EventTypeEnum.FINAL_EVENT), any())).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload, getDryRunFlag(),null,  eventId);

        //Assert
        verify(handleRegistry, times(1)).handleEvent(eq(ProductType.AR),eq(EventTypeEnum.FINAL_EVENT), any());
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_UNRECOGNIZED() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate("UNRECOGNIZED_STATUS");
        when(handleRegistry.handleEvent(eq(ProductType.UNKNOWN),eq(null), any())).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload, getDryRunFlag(),null,  eventId);

        //Assert
        verify(handleRegistry, times(1)).handleEvent(eq(ProductType.UNKNOWN),eq(null), any());
    }

    @Test
    void handleExternalChannelMessage_whenNotFoundException() {
        // Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN001C.name());
        when(handleRegistry.handleEvent(any(), any(), any()))
                .thenReturn(Mono.error(new PnPaperTrackerNotFoundException("ERROR", "Tracking not found")));

        // Act & Assert
        assertThrows(PnPaperTrackerNotFoundException.class, () ->
                externalChannelHandler.handleExternalChannelMessage(payload, true, null, eventId)
        );
    }

    @Test
    void handleExternalChannelMessage_whenSuccessfulProcessingWithDryRun() {
        // Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN004C.name());
        when(handleRegistry.handleEvent(eq(ProductType.AR), eq(EventTypeEnum.FINAL_EVENT), any()))
                .thenReturn(Mono.empty());

        // Act
        externalChannelHandler.handleExternalChannelMessage(payload, true, "reworkId", eventId);

        // Assert
        verify(handleRegistry, times(1))
                .handleEvent(eq(ProductType.AR), eq(EventTypeEnum.FINAL_EVENT), any());
    }

    private SingleStatusUpdate getSingleStatusUpdate(String statusCode) {
        SingleStatusUpdate singleStatusUpdate = new SingleStatusUpdate();
        PaperProgressStatusEvent analogMail = new PaperProgressStatusEvent();
        analogMail.setRequestId("test-request-id");
        analogMail.setStatusCode(statusCode);
        singleStatusUpdate.setAnalogMail(analogMail);
        return singleStatusUpdate;
    }

    private boolean getDryRunFlag() {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("dryRun", true);
        return (boolean) headers.getOrDefault("dryRun", false);
    }
}
