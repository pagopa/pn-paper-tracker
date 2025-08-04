package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.queue.model.DeliveryPushEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Objects;

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
        SendEvent event = new SendEvent();
        event.setStatusCode(StatusCodeEnum.PROGRESS);
        event.setStatusDetail("RECRN002A");
        AttachmentDetails attachment = new AttachmentDetails();
        attachment.setDate(OffsetDateTime.now());
        event.setAttachments(Collections.singletonList(attachment));
        event.setStatusDateTime(OffsetDateTime.now());
        event.setClientRequestTimeStamp(OffsetDateTime.now());

        String anonimizedDiscoveredAddress = "test-discovered-address";

        when(config.isSendOutputToDeliveryPush()).thenReturn(true);

        // Act
        deliveryPushSender.sendToOutputTarget(event, anonimizedDiscoveredAddress).block();

        // Assert
        verify(externalChannelOutputsMomProducer, times(1)).push(any(DeliveryPushEvent.class));
        verify(paperTrackerDryRunOutputsDAO, never()).insertOutputEvent(any());
    }

    @Test
    void testSendToOutputTarget_SendToDryRunOutputs() {
        // Arrange
        SendEvent event = new SendEvent();
        event.setStatusCode(StatusCodeEnum.PROGRESS);
        event.setStatusDetail("RECRN002A");
        AttachmentDetails attachment = new AttachmentDetails();
        attachment.setDate(OffsetDateTime.now());
        event.setAttachments(Collections.singletonList(attachment));
        event.setStatusDateTime(OffsetDateTime.now());
        event.setClientRequestTimeStamp(OffsetDateTime.now());

        String anonimizedDiscoveredAddress = "test-discovered-address";

        when(config.isSendOutputToDeliveryPush()).thenReturn(false);
        when(paperTrackerDryRunOutputsDAO.insertOutputEvent(any())).thenReturn(Mono.empty());

        // Act
        deliveryPushSender.sendToOutputTarget(event, anonimizedDiscoveredAddress).block();

        // Assert
        verify(paperTrackerDryRunOutputsDAO, times(1)).insertOutputEvent(any());
        verify(externalChannelOutputsMomProducer, never()).push(any(DeliveryPushEvent.class));
    }

    @Test
    void testBuildExternalChannelOutputEvent() {
        // Arrange
        SendEvent event = new SendEvent();
        event.setStatusCode(StatusCodeEnum.PROGRESS);
        event.setStatusDetail("RECRN002A");
        AttachmentDetails attachment = new AttachmentDetails();
        attachment.setDate(OffsetDateTime.now());
        event.setAttachments(Collections.singletonList(attachment));
        event.setStatusDateTime(OffsetDateTime.now());
        event.setClientRequestTimeStamp(OffsetDateTime.now());

        String anonimizedDiscoveredAddress = "test-discovered-address";

        when(config.isSendOutputToDeliveryPush()).thenReturn(true);

        // Act
        deliveryPushSender.sendToOutputTarget(event, anonimizedDiscoveredAddress).block();

        // Assert
        ArgumentCaptor<DeliveryPushEvent> captor = ArgumentCaptor.forClass(DeliveryPushEvent.class);
        verify(externalChannelOutputsMomProducer).push(captor.capture());
        Assertions.assertEquals("RECRN002A", Objects.requireNonNull(((DeliveryPushEvent) captor.getValue()).getPayload().getSendEvent()).getStatusDetail());
    }
}