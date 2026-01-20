package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.service.SourceQueueProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalChannelSourceEventsHandlerTest {

    @Mock
    private SourceQueueProxyService sourceQueueProxyService;

    private ExternalChannelSourceEventsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExternalChannelSourceEventsHandler(sourceQueueProxyService);
    }

    @Test
    void handleExternalChannelMessage_ok() {
        // Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate("REQ-TEST");

        Message<SingleStatusUpdate> message =
                MessageBuilder.withPayload(payload).build();

        when(sourceQueueProxyService.handleExternalChannelMessage(message))
                .thenReturn(Mono.empty());

        // Act
        assertDoesNotThrow(() -> handler.handleExternalChannelMessage(message));

        // Assert
        verify(sourceQueueProxyService, times(1))
                .handleExternalChannelMessage(message);
    }

    @Test
    void handleExternalChannelMessage_nullMessage_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> handler.handleExternalChannelMessage(null)
        );

        verifyNoInteractions(sourceQueueProxyService);
    }

    @Test
    void handleExternalChannelMessage_nullAnalogMail_throwsException() {
        // given
        SingleStatusUpdate payload = new SingleStatusUpdate();
        payload.setAnalogMail(null);

        Message<SingleStatusUpdate> message =
                MessageBuilder.withPayload(payload).build();

        // when / then
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> handler.handleExternalChannelMessage(message)
        );

        assertEquals("Payload or analogMail cannot be null", ex.getMessage());
        verifyNoInteractions(sourceQueueProxyService);
    }

    @Test
    void handleExternalChannelMessage_serviceReturnsError_propagatesException() {
        // Arrange
        SingleStatusUpdate payload = getSingleStatusUpdate("REQ-ERR");

        Message<SingleStatusUpdate> message =
                MessageBuilder.withPayload(payload).build();

        RuntimeException serviceException = new RuntimeException("Boom");

        when(sourceQueueProxyService.handleExternalChannelMessage(message))
                .thenReturn(Mono.error(serviceException));

        // Act / Assert
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> handler.handleExternalChannelMessage(message)
        );

        assertEquals("Boom", ex.getMessage());
        verify(sourceQueueProxyService).handleExternalChannelMessage(message);
    }

    private SingleStatusUpdate getSingleStatusUpdate(String requestId) {
        SingleStatusUpdate singleStatusUpdate = new SingleStatusUpdate();
        PaperProgressStatusEvent analogMail = new PaperProgressStatusEvent();
        analogMail.setRequestId(requestId);
        singleStatusUpdate.setAnalogMail(analogMail);
        return singleStatusUpdate;
    }
}
