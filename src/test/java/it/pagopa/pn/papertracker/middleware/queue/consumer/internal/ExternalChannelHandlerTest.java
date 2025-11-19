package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PaperTrackerExceptionHandler;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.UninitializedShipmentDryRunMomProducer;
import it.pagopa.pn.papertracker.middleware.queue.producer.UninitializedShipmentRunMomProducer;
import it.pagopa.pn.papertracker.model.EventTypeEnum;
import it.pagopa.pn.papertracker.service.handler_step.generic.HandlersRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.HashMap;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExternalChannelHandlerTest {

    @Mock
    private HandlersRegistry handleRegistry;

    @Mock
    private PaperTrackerExceptionHandler paperTrackerExceptionHandler;

    @Mock
    UninitializedShipmentDryRunMomProducer uninitializedShipmentDryRunProducer;

    @Mock
    UninitializedShipmentRunMomProducer uninitializedShipmentRunProducer;

    @Mock
    PnPaperTrackerConfigs pnPaperTrackerConfigs;

    private ExternalChannelHandler externalChannelHandler;

    private String eventId;

    @BeforeEach
    void setUp() {
        externalChannelHandler = new ExternalChannelHandler(
                                        paperTrackerExceptionHandler,
                                        handleRegistry,
                                        uninitializedShipmentDryRunProducer,
                                        uninitializedShipmentRunProducer,
                                        pnPaperTrackerConfigs);
        eventId = "eventId";
    }

    @Test
    void handleExternalChannelMessage_ignoredStatusCode() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate("REC090");
        when(pnPaperTrackerConfigs.getIgnoredStatusCodes()).thenReturn(java.util.List.of("REC090","CON991","CON992"));

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload, getDryRunFlag(),null);

        //Assert
        verify(handleRegistry, times(0)).handleEvent(eq(ProductType.AR),eq(EventTypeEnum.INTERMEDIATE_EVENT), any());
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_PROGRESS() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN002A.name());
        when(handleRegistry.handleEvent(eq(ProductType.AR),eq(EventTypeEnum.INTERMEDIATE_EVENT), any())).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload, getDryRunFlag(), eventId);

        //Assert
        verify(handleRegistry, times(1)).handleEvent(eq(ProductType.AR),eq(EventTypeEnum.INTERMEDIATE_EVENT), any());
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_KO() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN006.name());
        when(handleRegistry.handleEvent(eq(ProductType.AR),eq(EventTypeEnum.RETRYABLE_EVENT), any())).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload, getDryRunFlag(), eventId);

        //Assert
        verify(handleRegistry, times(1)).handleEvent(eq(ProductType.AR),eq(EventTypeEnum.RETRYABLE_EVENT), any());
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_OK() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN004C.name());
        when(handleRegistry.handleEvent(eq(ProductType.AR),eq(EventTypeEnum.FINAL_EVENT), any())).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload, getDryRunFlag(), eventId);

        //Assert
        verify(handleRegistry, times(1)).handleEvent(eq(ProductType.AR),eq(EventTypeEnum.FINAL_EVENT), any());
    }

    @Test
    void handleExternalChannelMessage_callsAREventHandler_UNRECOGNIZED() {
        //Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate("UNRECOGNIZED_STATUS");
        when(handleRegistry.handleEvent(eq(ProductType.UNKNOWN),eq(null), any())).thenReturn(Mono.empty());

        //Act
        externalChannelHandler.handleExternalChannelMessage(payload, getDryRunFlag(), eventId);

        //Assert
        verify(handleRegistry, times(1)).handleEvent(eq(ProductType.UNKNOWN),eq(null), any());
    }

    @Test
    void handleExternalChannelMessage_whenNotFoundExceptionWithDryRun_shouldPushToDryRunQueue() {
        // Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN001C.name());
        when(handleRegistry.handleEvent(any(), any(), any()))
                .thenReturn(Mono.error(new PnPaperTrackerNotFoundException("ERROR", "Tracking not found")));

        // Act
        externalChannelHandler.handleExternalChannelMessage(payload, true, eventId);

        // Assert
        ArgumentCaptor<ExternalChannelEvent> captor =
                ArgumentCaptor.forClass(it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent.class);

        verify(uninitializedShipmentDryRunProducer, times(1)).push(captor.capture());
        verifyNoInteractions(uninitializedShipmentRunProducer);

        var capturedEvent = captor.getValue();
        assertThat(capturedEvent.getHeader().getDryRun()).isTrue();
        assertThat(capturedEvent.getHeader().getEventId()).isEqualTo(eventId);
        assertThat(capturedEvent.getPayload()).isEqualTo(payload);
    }

    @Test
    void handleExternalChannelMessage_whenNotFoundExceptionWithoutDryRun_shouldPushToRunQueue() {
        // Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN001C.name());
        when(handleRegistry.handleEvent(any(), any(), any()))
                .thenReturn(Mono.error(new PnPaperTrackerNotFoundException("ERROR", "Tracking not found")));

        // Act
        externalChannelHandler.handleExternalChannelMessage(payload, false, eventId);

        // Assert
        ArgumentCaptor<it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent> captor =
                ArgumentCaptor.forClass(it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent.class);

        verify(uninitializedShipmentRunProducer, times(1)).push(captor.capture());
        verifyNoInteractions(uninitializedShipmentDryRunProducer);

        var capturedEvent = captor.getValue();
        assertThat(capturedEvent.getHeader().getDryRun()).isFalse();
        assertThat(capturedEvent.getHeader().getEventId()).isEqualTo(eventId);
        assertThat(capturedEvent.getPayload()).isEqualTo(payload);
    }

    @Test
    void handleExternalChannelMessage_whenNotFoundExceptionMultipleTimes_shouldRouteCorrectly() {
        // Arrange
        SingleStatusUpdate payload1 = getSingleStatusUpdate(RECRN001C.name());
        payload1.getAnalogMail().setRequestId("request-1");

        SingleStatusUpdate payload2 = getSingleStatusUpdate(RECRN001C.name());
        payload2.getAnalogMail().setRequestId("request-2");

        when(handleRegistry.handleEvent(any(), any(), any()))
                .thenReturn(Mono.error(new PnPaperTrackerNotFoundException("ERROR", "Tracking not found")));

        // Act - primo messaggio con dry-run
        externalChannelHandler.handleExternalChannelMessage(payload1, true, "event-1");

        // Act - secondo messaggio senza dry-run
        externalChannelHandler.handleExternalChannelMessage(payload2, false, "event-2");

        // Assert
        ArgumentCaptor<it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent> dryRunCaptor =
                ArgumentCaptor.forClass(it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent.class);
        ArgumentCaptor<it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent> runCaptor =
                ArgumentCaptor.forClass(it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent.class);

        verify(uninitializedShipmentDryRunProducer, times(1)).push(dryRunCaptor.capture());
        verify(uninitializedShipmentRunProducer, times(1)).push(runCaptor.capture());

        // Verifica contenuto messaggio dry-run
        var dryRunEvent = dryRunCaptor.getValue();
        assertThat(dryRunEvent.getHeader().getDryRun()).isTrue();
        assertThat(dryRunEvent.getHeader().getEventId()).isEqualTo("event-1");
        assertThat(dryRunEvent.getPayload().getAnalogMail().getRequestId()).isEqualTo("request-1");

        // Verifica contenuto messaggio run
        var runEvent = runCaptor.getValue();
        assertThat(runEvent.getHeader().getDryRun()).isFalse();
        assertThat(runEvent.getHeader().getEventId()).isEqualTo("event-2");
        assertThat(runEvent.getPayload().getAnalogMail().getRequestId()).isEqualTo("request-2");
    }

    @Test
    void handleExternalChannelMessage_whenSuccessfulProcessingWithDryRun_shouldNotPushToAnyQueue() {
        // Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate(RECRN004C.name());
        when(handleRegistry.handleEvent(eq(ProductType.AR), eq(EventTypeEnum.FINAL_EVENT), any()))
                .thenReturn(Mono.empty());

        // Act
        externalChannelHandler.handleExternalChannelMessage(payload, true, eventId);

        // Assert
        verify(handleRegistry, times(1))
                .handleEvent(eq(ProductType.AR), eq(EventTypeEnum.FINAL_EVENT), any());
        verifyNoInteractions(uninitializedShipmentDryRunProducer);
        verifyNoInteractions(uninitializedShipmentRunProducer);
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
