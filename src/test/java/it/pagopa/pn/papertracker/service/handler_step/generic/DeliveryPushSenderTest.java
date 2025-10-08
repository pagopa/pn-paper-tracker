package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperStatus;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.middleware.queue.model.DeliveryPushEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DeliveryPushSenderTest {

    @Mock
    private PnPaperTrackerConfigs config;

    @Mock
    private PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @Mock
    private ExternalChannelOutputsMomProducer externalChannelOutputsMomProducer;

    @InjectMocks
    private DeliveryPushSender deliveryPushSender;

    @Test
    void testSendToOutputTarget_SendToExternalChannelOutputs() {
        // Arrange
        SendEvent event = getSendEvent();
        PaperTrackings paperTrackings = new PaperTrackings();
        HandlerContext context = new HandlerContext();
        context.setPaperTrackings(paperTrackings);

        // Act
        deliveryPushSender.sendToOutputTarget(event, context).block();

        // Assert
        verify(externalChannelOutputsMomProducer, times(1)).push(any(DeliveryPushEvent.class));
        verify(paperTrackerDryRunOutputsDAO, never()).insertOutputEvent(any());
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
        deliveryPushSender.sendToOutputTarget(event, context).block();

        // Assert
        verify(paperTrackerDryRunOutputsDAO, times(1)).insertOutputEvent(any());
        verify(externalChannelOutputsMomProducer, never()).push(any(DeliveryPushEvent.class));
    }

    @Test
    void testBuildDeliveryPushEvent() {
        // Arrange
        SendEvent event = getSendEvent();
        PaperTrackings paperTrackings = new PaperTrackings();
        HandlerContext context = new HandlerContext();
        context.setPaperTrackings(paperTrackings);

        // Act
        deliveryPushSender.sendToOutputTarget(event, context).block();

        // Assert
        ArgumentCaptor<DeliveryPushEvent> captor = ArgumentCaptor.forClass(DeliveryPushEvent.class);
        verify(externalChannelOutputsMomProducer).push(captor.capture());
        Assertions.assertEquals(event, captor.getValue().getPayload().getSendEvent());
    }

    @Test
    void testExecuteFinalStatusCodeNullAndNextRequestIdPcretryNull() {
        // Arrange
        SendEvent event = getSendEvent();
        HandlerContext context = new HandlerContext();
        PaperTrackings paperTrackings = new PaperTrackings();
        context.setPaperTrackings(paperTrackings);
        context.setEventsToSend(Collections.singletonList(event));

        // Act
        deliveryPushSender.execute(context).block();

        // Assert
        verify(externalChannelOutputsMomProducer, times(1)).push(any(DeliveryPushEvent.class));
        verify(paperTrackerDryRunOutputsDAO, never()).insertOutputEvent(any());
        verify(paperTrackingsDAO, never()).updateItem(anyString(), any(PaperTrackings.class));
    }

    @Test
    void testExecuteFinalStatusCodeNotNull() {
        // Arrange
        SendEvent event = getSendEvent();
        HandlerContext context = new HandlerContext();
        context.setFinalStatusCode("RECRN001C");
        PaperTrackings paperTrackings = new PaperTrackings();
        context.setPaperTrackings(paperTrackings);
        context.setEventsToSend(Collections.singletonList(event));

        // Act
        deliveryPushSender.execute(context).block();

        // Assert
        verify(externalChannelOutputsMomProducer, times(1)).push(any(DeliveryPushEvent.class));
        verify(paperTrackerDryRunOutputsDAO, never()).insertOutputEvent(any());
        ArgumentCaptor<PaperTrackings> captor = ArgumentCaptor.forClass(PaperTrackings.class);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), captor.capture());
        Assertions.assertEquals(PaperTrackingsState.DONE, captor.getValue().getState());
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