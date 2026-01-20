package it.pagopa.pn.papertracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProcessingMode;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelToPaperChannelDryRunMomProducer;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelToPaperTrackerMomProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SourceQueueProxyServiceImplTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ExternalChannelToPaperTrackerMomProducer paperTrackerProducer;

    @Mock
    private ExternalChannelToPaperChannelDryRunMomProducer paperChannelDryRunProducer;

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @InjectMocks
    private SourceQueueProxyServiceImpl service;

    @Captor
    private ArgumentCaptor<ExternalChannelEvent> eventCaptor;

    private Message<SingleStatusUpdate> message;
    private SingleStatusUpdate statusUpdate;
    private PaperProgressStatusEvent analogMail;
    private static final String REQUEST_ID = "test-request-id-123";

    @BeforeEach
    void setUp() {
        analogMail = new PaperProgressStatusEvent();
        analogMail.setRequestId(REQUEST_ID);

        statusUpdate = new SingleStatusUpdate();
        statusUpdate.setAnalogMail(analogMail);

        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        message = new Message<>() {
            @Override
            public SingleStatusUpdate getPayload() {
                return statusUpdate;
            }

            @Override
            public MessageHeaders getHeaders() {
                return new MessageHeaders(headers);
            }
        };
    }

    @Test
    void handleExternalChannelMessage_trackingNotFound_shouldSendToPaperChannelOnly() {
        // Arrange
        when(paperTrackingsDAO.retrieveEntityByTrackingId(REQUEST_ID))
                .thenReturn(Mono.empty());

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(message);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(paperChannelDryRunProducer, times(1)).push(any(ExternalChannelEvent.class));
        verify(paperTrackerProducer, never()).push(any(ExternalChannelEvent.class));
        verify(paperTrackingsDAO, times(1)).retrieveEntityByTrackingId(REQUEST_ID);
    }

    @Test
    void handleExternalChannelMessage_nullProcessingMode_shouldSendToPaperChannelOnly() {
        // Arrange
        PaperTrackings tracking = new PaperTrackings();
        tracking.setProcessingMode(null);
        tracking.setTrackingId(REQUEST_ID);

        when(paperTrackingsDAO.retrieveEntityByTrackingId(REQUEST_ID))
                .thenReturn(Mono.just(tracking));

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(message);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(paperChannelDryRunProducer, times(1)).push(any(ExternalChannelEvent.class));
        verify(paperTrackerProducer, never()).push(any(ExternalChannelEvent.class));
        verify(paperTrackingsDAO, times(1)).retrieveEntityByTrackingId(REQUEST_ID);
    }

    @Test
    void handleExternalChannelMessage_dryMode_shouldSendToBothQueues() {
        // Arrange
        PaperTrackings tracking = new PaperTrackings();
        tracking.setProcessingMode(ProcessingMode.DRY);
        tracking.setTrackingId(REQUEST_ID);

        when(paperTrackingsDAO.retrieveEntityByTrackingId(REQUEST_ID))
                .thenReturn(Mono.just(tracking));

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(message);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(paperChannelDryRunProducer, times(1)).push(eventCaptor.capture());
        verify(paperTrackerProducer, times(1)).push(eventCaptor.capture());
        verify(paperTrackingsDAO, times(1)).retrieveEntityByTrackingId(REQUEST_ID);

        // Verifica che l'evento abbia il flag dryRun impostato a true
        var capturedEvents = eventCaptor.getAllValues();
        assertEquals(2, capturedEvents.size());
        capturedEvents.forEach(event -> {
            assertNotNull(event.getHeader());
            assertNotNull(event.getPayload());
            assertEquals(statusUpdate, event.getPayload());
        });
    }

    @Test
    void handleExternalChannelMessage_runMode_shouldSendToPaperTrackerOnly() {
        // Arrange
        PaperTrackings tracking = new PaperTrackings();
        tracking.setProcessingMode(ProcessingMode.RUN);
        tracking.setTrackingId(REQUEST_ID);

        when(paperTrackingsDAO.retrieveEntityByTrackingId(REQUEST_ID))
                .thenReturn(Mono.just(tracking));

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(message);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(paperTrackerProducer, times(1)).push(eventCaptor.capture());
        verify(paperChannelDryRunProducer, never()).push(any(ExternalChannelEvent.class));
        verify(paperTrackingsDAO, times(1)).retrieveEntityByTrackingId(REQUEST_ID);

        // Verifica l'evento catturato
        ExternalChannelEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent.getHeader());
        assertEquals(statusUpdate, capturedEvent.getPayload());
    }

    @Test
    void handleExternalChannelMessage_nullAnalogMail_shouldReturnError() {
        // Arrange
        SingleStatusUpdate invalidUpdate = new SingleStatusUpdate();
        invalidUpdate.setAnalogMail(null);

        Message<SingleStatusUpdate> invalidMessage = new Message<>() {
            @Override
            public SingleStatusUpdate getPayload() {
                return invalidUpdate;
            }

            @Override
            public MessageHeaders getHeaders() {
                return new MessageHeaders(new HashMap<>());
            }
        };

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(invalidMessage);

        // Assert
        StepVerifier.create(result)
                .expectError(PaperTrackerException.class)
                .verify();

        verify(paperTrackingsDAO, never()).retrieveEntityByTrackingId(any());
        verify(paperTrackerProducer, never()).push((ExternalChannelEvent) any());
        verify(paperChannelDryRunProducer, never()).push((ExternalChannelEvent) any());
    }

    @Test
    void handleExternalChannelMessage_nullRequestId_shouldReturnError() {
        // Arrange
        PaperProgressStatusEvent mailWithoutRequestId = new PaperProgressStatusEvent();
        mailWithoutRequestId.setRequestId(null);

        SingleStatusUpdate updateWithoutRequestId = new SingleStatusUpdate();
        updateWithoutRequestId.setAnalogMail(mailWithoutRequestId);

        Message<SingleStatusUpdate> messageWithoutRequestId = new Message<>() {
            @Override
            public SingleStatusUpdate getPayload() {
                return updateWithoutRequestId;
            }

            @Override
            public MessageHeaders getHeaders() {
                return new MessageHeaders(new HashMap<>());
            }
        };

        when(paperTrackingsDAO.retrieveEntityByTrackingId(null))
                .thenReturn(Mono.empty());

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(messageWithoutRequestId);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void handleExternalChannelMessage_daoError_shouldPropagateError() {
        // Arrange
        RuntimeException daoException = new RuntimeException("DAO error");
        when(paperTrackingsDAO.retrieveEntityByTrackingId(REQUEST_ID))
                .thenReturn(Mono.error(daoException));

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(message);

        // Assert
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(paperTrackerProducer, never()).push((ExternalChannelEvent) any());
        verify(paperChannelDryRunProducer, never()).push((ExternalChannelEvent) any());
    }

    @Test
    void buildOutputMessage_shouldPropagateOriginalMessageHeaders() {
        // Arrange
        Map<String, Object> originalHeaders = new HashMap<>();
        originalHeaders.put("customHeader1", "customValue1");
        originalHeaders.put("customHeader2", 12345);
        originalHeaders.put("customHeader3", true);

        Message<SingleStatusUpdate> messageWithHeaders = new Message<>() {
            @Override
            public SingleStatusUpdate getPayload() {
                return statusUpdate;
            }

            @Override
            public MessageHeaders getHeaders() {
                return new MessageHeaders(originalHeaders);
            }
        };

        PaperTrackings tracking = new PaperTrackings();
        tracking.setProcessingMode(ProcessingMode.RUN);
        tracking.setTrackingId(REQUEST_ID);

        when(paperTrackingsDAO.retrieveEntityByTrackingId(REQUEST_ID))
                .thenReturn(Mono.just(tracking));

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(messageWithHeaders);

        // Assert
        StepVerifier.create(result).verifyComplete();

        verify(paperTrackerProducer).push(eventCaptor.capture());

        ExternalChannelEvent event = eventCaptor.getValue();
        assertNotNull(event);
        assertNotNull(event.getHeader());
        assertNotNull(event.getHeader().getMessageAttributes());

        // Verifica che gli header originali siano stati propagati
        var messageAttributes = event.getHeader().getMessageAttributes();
        assertTrue(messageAttributes.containsKey("customHeader1"));
        assertTrue(messageAttributes.containsKey("customHeader2"));
        assertTrue(messageAttributes.containsKey("customHeader3"));

        assertEquals("customValue1", messageAttributes.get("customHeader1").stringValue());
        assertEquals("12345", messageAttributes.get("customHeader2").stringValue());
        assertEquals("true", messageAttributes.get("customHeader3").stringValue());

        // Verifica che sia stato aggiunto anche il dryRun header
        assertTrue(messageAttributes.containsKey("dryRun"));
        assertEquals("false", messageAttributes.get("dryRun").stringValue());
    }


    @Test
    void handleExternalChannelMessage_dryMode_shouldSetDryRunHeaderToTrue() {
        // Arrange
        PaperTrackings tracking = new PaperTrackings();
        tracking.setProcessingMode(ProcessingMode.DRY);
        when(paperTrackingsDAO.retrieveEntityByTrackingId(REQUEST_ID))
                .thenReturn(Mono.just(tracking));

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(message);

        // Assert
        StepVerifier.create(result).verifyComplete();

        verify(paperChannelDryRunProducer).push(eventCaptor.capture());

        ExternalChannelEvent event = eventCaptor.getValue();
        assertNotNull(event.getHeader().getMessageAttributes());
    }

    @Test
    void handleExternalChannelMessage_runMode_shouldSetDryRunHeaderToFalse() {
        // Arrange
        PaperTrackings tracking = new PaperTrackings();
        tracking.setProcessingMode(ProcessingMode.RUN);
        when(paperTrackingsDAO.retrieveEntityByTrackingId(REQUEST_ID))
                .thenReturn(Mono.just(tracking));

        // Act
        Mono<Void> result = service.handleExternalChannelMessage(message);

        // Assert
        StepVerifier.create(result).verifyComplete();

        verify(paperTrackerProducer).push(eventCaptor.capture());

        ExternalChannelEvent event = eventCaptor.getValue();
        assertNotNull(event.getHeader().getMessageAttributes());
    }
}