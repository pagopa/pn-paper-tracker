package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperChannelUpdate;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.eventBridge.EventBridgePublisher;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.utils.LogUtility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.time.OffsetDateTime;
import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OutputTargetSenderTest {

    @Mock
    private PnPaperTrackerConfigs config;

    @Mock
    private PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @Mock
    private EventBridgePublisher eventBridgePublisher;

    @Mock
    private LogUtility logUtility;

    @InjectMocks
    private OutputTargetSender outputTargetSender;

    @Test
    void testSendToOutputTarget_SendToExternalChannelOutputs() {
        // Arrange
        SendEvent event = getSendEvent();
        PaperTrackings paperTrackings = new PaperTrackings();
        HandlerContext context = new HandlerContext();
        context.setPaperTrackings(paperTrackings);
        when(eventBridgePublisher.publish(any(PaperChannelUpdate.class))).thenReturn(Mono.just(PutEventsResponse.builder().build()));
        when(paperTrackerDryRunOutputsDAO.insertOutputEvent(any())).thenReturn(Mono.empty());

        // Act
        outputTargetSender.sendToOutputTarget(event, context).block();

        // Assert
        verify(eventBridgePublisher, times(1)).publish(any(PaperChannelUpdate.class));
        ArgumentCaptor<PaperTrackerDryRunOutputs> captor = ArgumentCaptor.forClass(PaperTrackerDryRunOutputs.class);
        verify(paperTrackerDryRunOutputsDAO, times(1)).insertOutputEvent(captor.capture());
        Assertions.assertEquals(ProcessingMode.RUN, captor.getValue().getProcessingMode());
        verifyNoInteractions(paperTrackingsDAO);
    }

    @Test
    void testSendToOutputTarget_SendToDryRunOutputs() {
        // Arrange
        SendEvent event = getSendEvent();
        HandlerContext context = new HandlerContext();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setPaperStatus(new PaperStatus());
        context.setDryRunEnabled(true);
        context.setPaperTrackings(paperTrackings);

        when(paperTrackerDryRunOutputsDAO.insertOutputEvent(any())).thenReturn(Mono.empty());

        // Act
        outputTargetSender.sendToOutputTarget(event, context).block();

        // Assert
        ArgumentCaptor<PaperTrackerDryRunOutputs> captor = ArgumentCaptor.forClass(PaperTrackerDryRunOutputs.class);
        verify(paperTrackerDryRunOutputsDAO, times(1)).insertOutputEvent(captor.capture());
        Assertions.assertEquals(ProcessingMode.DRY, captor.getValue().getProcessingMode());
        verify(eventBridgePublisher, never()).publish(any(PaperChannelUpdate.class));
        verifyNoInteractions(paperTrackingsDAO);
    }

    @Test
    void testBuildDeliveryPushEvent() {
        // Arrange
        SendEvent event = getSendEvent();
        PaperTrackings paperTrackings = new PaperTrackings();
        HandlerContext context = new HandlerContext();
        context.setPaperTrackings(paperTrackings);
        when(eventBridgePublisher.publish(any(PaperChannelUpdate.class))).thenReturn(Mono.just(PutEventsResponse.builder().build()));
        when(paperTrackerDryRunOutputsDAO.insertOutputEvent(any())).thenReturn(Mono.empty());

        // Act
        outputTargetSender.sendToOutputTarget(event, context).block();

        // Assert
        ArgumentCaptor<PaperChannelUpdate> captor = ArgumentCaptor.forClass(PaperChannelUpdate.class);
        verify(eventBridgePublisher).publish(captor.capture());
        Assertions.assertEquals(event, captor.getValue().getSendEvent());
        verifyNoInteractions(paperTrackingsDAO);
        verify(paperTrackerDryRunOutputsDAO, times(1)).insertOutputEvent(any());
    }

    @Test
    void testSendToOutputTarget_EventBridgePublishError() {
        // Arrange
        SendEvent event = getSendEvent();
        PaperTrackings paperTrackings = new PaperTrackings();
        HandlerContext context = new HandlerContext();
        context.setPaperTrackings(paperTrackings);
        RuntimeException publishError = new RuntimeException("publish failed");
        when(eventBridgePublisher.publish(any(PaperChannelUpdate.class))).thenReturn(Mono.error(publishError));

        // Act & Assert
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> outputTargetSender.sendToOutputTarget(event, context).block());

        Assertions.assertEquals(publishError, thrown);
        verify(eventBridgePublisher, times(1)).publish(any(PaperChannelUpdate.class));
        verify(paperTrackerDryRunOutputsDAO, never()).insertOutputEvent(any());
    }

    @Test
    void testExecuteFinalStatusCodeNullAndNextRequestIdPcretryNull() {
        // Arrange
        SendEvent event = getSendEvent();
        HandlerContext context = new HandlerContext();
        PaperTrackings paperTrackings = new PaperTrackings();
        context.setPaperTrackings(paperTrackings);
        context.setEventsToSend(Collections.singletonList(event));
        when(eventBridgePublisher.publish(any(PaperChannelUpdate.class))).thenReturn(Mono.just(PutEventsResponse.builder().build()));
        when(paperTrackerDryRunOutputsDAO.insertOutputEvent(any())).thenReturn(Mono.empty());

        // Act
        outputTargetSender.execute(context).block();

        // Assert
        verify(eventBridgePublisher, times(1)).publish(any(PaperChannelUpdate.class));
        verify(paperTrackerDryRunOutputsDAO, times(1)).insertOutputEvent(any());
        verify(paperTrackingsDAO, never()).updateItem(anyString(), any(PaperTrackings.class));
        verifyNoInteractions(paperTrackingsDAO);
    }

    @Test
    void testExecuteFinalStatusCodeNotNull() {
        // Arrange
        HandlerContext context = getFinalEventHandlerContext();
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(new PaperTrackings()));
        when(eventBridgePublisher.publish(any(PaperChannelUpdate.class))).thenReturn(Mono.just(PutEventsResponse.builder().build()));
        when(paperTrackerDryRunOutputsDAO.insertOutputEvent(any())).thenReturn(Mono.empty());

        // Act
        outputTargetSender.execute(context).block();

        // Assert
        verify(eventBridgePublisher, times(1)).publish(any(PaperChannelUpdate.class));
        verify(paperTrackerDryRunOutputsDAO,times(1)).insertOutputEvent(any());
        ArgumentCaptor<PaperTrackings> captor = ArgumentCaptor.forClass(PaperTrackings.class);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), captor.capture());
        Assertions.assertEquals(PaperTrackingsState.DONE, captor.getValue().getState());
    }

    @Test
    void testExecuteMextPcRetryNotNull() {
        // Arrange
        HandlerContext context = getPcRetryHandlerContext();
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(new PaperTrackings()));
        when(eventBridgePublisher.publish(any(PaperChannelUpdate.class))).thenReturn(Mono.just(PutEventsResponse.builder().build()));
        when(paperTrackerDryRunOutputsDAO.insertOutputEvent(any())).thenReturn(Mono.empty());

        // Act
        outputTargetSender.execute(context).block();

        // Assert
        verify(eventBridgePublisher, times(1)).publish(any(PaperChannelUpdate.class));
        verify(paperTrackerDryRunOutputsDAO, times(1)).insertOutputEvent(any());
        ArgumentCaptor<PaperTrackings> captor = ArgumentCaptor.forClass(PaperTrackings.class);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), captor.capture());
        Assertions.assertEquals(PaperTrackingsState.DONE, captor.getValue().getState());
    }

    private HandlerContext getFinalEventHandlerContext() {
        SendEvent event = new SendEvent();
        event.setStatusCode(StatusCodeEnum.OK);
        event.setStatusDetail("RECRN001C");
        HandlerContext context = new HandlerContext();
        context.setFinalStatusCode("RECRN001C");
        PaperTrackings paperTrackings = new PaperTrackings();
        context.setPaperTrackings(paperTrackings);
        context.setEventsToSend(Collections.singletonList(event));
        return context;
    }

    private HandlerContext getPcRetryHandlerContext() {
        SendEvent event = new SendEvent();
        event.setStatusCode(StatusCodeEnum.PROGRESS);
        event.setStatusDetail("RECRN006");
        HandlerContext context = new HandlerContext();
        context.setNextRequestIdPcRetry("nextRequestIdPcRetry");
        PaperTrackings paperTrackings = new PaperTrackings();
        context.setPaperTrackings(paperTrackings);
        context.setEventsToSend(Collections.singletonList(event));
        return context;
    }


    private SendEvent getSendEvent() {
        SendEvent event = new SendEvent();
        event.setStatusCode(StatusCodeEnum.PROGRESS);
        AttachmentDetails attachment = new AttachmentDetails();
        attachment.setDate(OffsetDateTime.now());
        event.setAttachments(Collections.singletonList(attachment));
        event.setStatusDateTime(OffsetDateTime.now());
        event.setClientRequestTimeStamp(OffsetDateTime.now());
        return event;
    }

}