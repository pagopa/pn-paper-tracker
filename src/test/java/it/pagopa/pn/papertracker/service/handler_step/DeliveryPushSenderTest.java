package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.NotificationState;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelOutputEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DeliveryPushSenderTest {

    @Mock
    private PnPaperTrackerConfigs config;

    @Mock
    private PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;

    @Mock
    private ExternalChannelOutputsMomProducer externalChannelOutputsMomProducer;

    @InjectMocks
    private DeliveryPushSender deliveryPushSender;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSendToOutputTarget_SendToExternalChannelOutputs() {
        // Arrange
        Event event = new Event();
        event.setStatusCode("RECRN002A");
        Attachment attachment = new Attachment();
        attachment.setDate(Instant.now());
        event.setAttachments(Collections.singletonList(attachment));
        event.setStatusTimestamp(Instant.now());
        event.setRequestTimestamp(Instant.now());

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId("REQUEST_ID");
        paperTrackings.setNotificationState(new NotificationState());
        paperTrackings.getNotificationState().setRegisteredLetterCode("LETTER_CODE");

        when(config.isSendOutputToDeliveryPush()).thenReturn(true);

        // Act
        deliveryPushSender.sendToOutputTarget(event, paperTrackings);

        // Assert
        verify(externalChannelOutputsMomProducer, times(1)).push(any(ExternalChannelOutputEvent.class));
        verify(paperTrackerDryRunOutputsDAO, never()).insertOutputEvent(any());
    }

    @Test
    void testSendToOutputTarget_SendToDryRunOutputs() {
        // Arrange
        Event event = new Event();
        event.setStatusCode("RECRN002A");
        Attachment attachment = new Attachment();
        attachment.setDate(Instant.now());
        event.setAttachments(Collections.singletonList(attachment));
        event.setStatusTimestamp(Instant.now());
        event.setRequestTimestamp(Instant.now());

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId("REQUEST_ID");
        paperTrackings.setNotificationState(new NotificationState());
        paperTrackings.getNotificationState().setRegisteredLetterCode("LETTER_CODE");

        when(config.isSendOutputToDeliveryPush()).thenReturn(false);

        // Act
        deliveryPushSender.sendToOutputTarget(event, paperTrackings);

        // Assert
        verify(paperTrackerDryRunOutputsDAO, times(1)).insertOutputEvent(any());
        verify(externalChannelOutputsMomProducer, never()).push(any(ExternalChannelOutputEvent.class));
    }

    @Test
    void testBuildExternalChannelOutputEvent() {
        // Arrange
        Event event = new Event();
        event.setStatusCode("RECRN002A");

        Attachment attachment = new Attachment();
        attachment.setDate(Instant.now());
        event.setAttachments(Collections.singletonList(attachment));
        event.setStatusTimestamp(Instant.now());
        event.setRequestTimestamp(Instant.now());

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId("REQUEST_ID");
        paperTrackings.setNotificationState(new NotificationState());
        paperTrackings.getNotificationState().setRegisteredLetterCode("LETTER_CODE");

        when(config.isSendOutputToDeliveryPush()).thenReturn(true);

        // Act
        deliveryPushSender.sendToOutputTarget(event, paperTrackings);

        // Assert
        ArgumentCaptor<ExternalChannelOutputEvent> captor = ArgumentCaptor.forClass(ExternalChannelOutputEvent.class);
        verify(externalChannelOutputsMomProducer).push(captor.capture());
        assertEquals("REQUEST_ID", ((ExternalChannelOutputEvent) captor.getValue()).getPayload().getRequestId());
    }
}