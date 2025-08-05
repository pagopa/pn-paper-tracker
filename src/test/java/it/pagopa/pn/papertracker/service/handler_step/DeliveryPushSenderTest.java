package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.queue.model.DeliveryPushEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
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

@ExtendWith(MockitoExtension.class)
class DeliveryPushSenderTest {

    @Mock
    private PnPaperTrackerConfigs config;

    @Mock
    private PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;

    @Mock
    private ExternalChannelOutputsMomProducer externalChannelOutputsMomProducer;

    @InjectMocks
    private DeliveryPushSender deliveryPushSender;

    @Test
    void testSendToOutputTarget_SendToExternalChannelOutputs() {
        // Arrange
        SendEvent event = getSendEvent();
        String discoveredAddress = "123 Main St";

        when(config.isSendOutputToDeliveryPush()).thenReturn(true);

        // Act
        deliveryPushSender.sendToOutputTarget(event, discoveredAddress).block();

        // Assert
        verify(externalChannelOutputsMomProducer, times(1)).push(any(DeliveryPushEvent.class));
        verify(paperTrackerDryRunOutputsDAO, never()).insertOutputEvent(any());
    }

    @Test
    void testSendToOutputTarget_SendToDryRunOutputs() {
        // Arrange
        SendEvent event = getSendEvent();
        String discoveredAddress = "123 Main St";

        when(config.isSendOutputToDeliveryPush()).thenReturn(false);
        when(paperTrackerDryRunOutputsDAO.insertOutputEvent(any())).thenReturn(Mono.empty());

        // Act
        deliveryPushSender.sendToOutputTarget(event, discoveredAddress).block();

        // Assert
        verify(paperTrackerDryRunOutputsDAO, times(1)).insertOutputEvent(any());
        verify(externalChannelOutputsMomProducer, never()).push(any(DeliveryPushEvent.class));
    }

    @Test
    void testBuildDeliveryPushEvent() {
        // Arrange
        SendEvent event = getSendEvent();
        String discoveredAddress = "123 Main St";

        when(config.isSendOutputToDeliveryPush()).thenReturn(true);

        // Act
        deliveryPushSender.sendToOutputTarget(event, discoveredAddress).block();

        // Assert
        ArgumentCaptor<DeliveryPushEvent> captor = ArgumentCaptor.forClass(DeliveryPushEvent.class);
        verify(externalChannelOutputsMomProducer).push(captor.capture());
        Assertions.assertEquals(event, captor.getValue().getPayload().getSendEvent());
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