package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static it.pagopa.pn.papertracker.config.StatusCodeConfiguration.StatusCodeConfigurationEnum.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalEventBuilderTest {

    @Mock
    PnPaperTrackerConfigs cfg;

    FinalEventBuilder finalEventBuilder;

    HandlerContext handlerContext;

    PaperTrackings paperTrackings;

    StatusCodeConfiguration statusCodeConfiguration;

    @BeforeEach
    void setUp() {
        handlerContext = new HandlerContext();
        statusCodeConfiguration = new StatusCodeConfiguration();
        finalEventBuilder = new FinalEventBuilder(cfg, statusCodeConfiguration);
        paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId("req-123");
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        paperTrackings.setNotificationState(new NotificationState());
        paperTrackings.getNotificationState().setRegisteredLetterCode("RL123");
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setSequencesValidationTimestamp(Instant.now());
        paperTrackings.setValidationFlow(validationFlow);
    }

    @Test
    void buildFinalEvent_stockStatus_differenceGreater_RECRN005C() {
        // Arrange
        setValidatedEvents(RECRN005A.name(), Instant.now().minus(40, ChronoUnit.DAYS));
        PaperProgressStatusEvent finalEvent = getFinalEvent(RECRN005C.name());
        when(cfg.getCompiutaGiacenzaArDuration()).thenReturn(Duration.ofDays(30));

        // Act
        StepVerifier.create(finalEventBuilder.buildFinalEvent(paperTrackings, finalEvent, handlerContext))
                .verifyComplete();

        // Assert
        Assertions.assertEquals(2, handlerContext.getEventsToSend().size());
        Assertions.assertEquals(PNRN012.name(), handlerContext.getEventsToSend().getFirst().getStatusDetail());
        Assertions.assertEquals(StatusCodeEnum.PROGRESS, handlerContext.getEventsToSend().getLast().getStatusCode());
    }

    @Test
    void buildFinalEvent_stockStatus_differenceMinor_RECRN005C() {
        // Arrange
        setValidatedEvents(RECRN005A.name(), Instant.now().minus(25, ChronoUnit.DAYS));
        PaperProgressStatusEvent finalEvent = getFinalEvent(RECRN005C.name());
        when(cfg.getCompiutaGiacenzaArDuration()).thenReturn(Duration.ofDays(30));

        // Act
        StepVerifier.create(finalEventBuilder.buildFinalEvent(paperTrackings, finalEvent, handlerContext))
                .expectErrorSatisfies(throwable -> {
                    Assertions.assertInstanceOf(PnPaperTrackerValidationException.class, throwable);
                    PnPaperTrackerValidationException ex = (PnPaperTrackerValidationException) throwable;
                    Assertions.assertNotNull(ex.getError());
                    Assertions.assertEquals(paperTrackings.getTrackingId(), ex.getError().getRequestId());
                    Assertions.assertEquals(finalEvent.getStatusCode(), ex.getError().getEventThrow());
                    Assertions.assertEquals(ProductType.valueOf(finalEvent.getProductType()), ex.getError().getProductType());
                    Assertions.assertEquals(ErrorCause.GIACENZA_DATE_ERROR, ex.getError().getDetails().getCause());
                })
                .verify();

        // Assert
        Assertions.assertTrue(handlerContext.getEventsToSend().isEmpty());
    }

    @Test
    void buildFinalEvent_stockStatus_differenceMinor_RECRN003C() {
        // Arrange
        setValidatedEvents(RECRN003A.name(), Instant.now().minus(5, ChronoUnit.DAYS));
        PaperProgressStatusEvent finalEvent = getFinalEvent(RECRN003C.name());
        when(cfg.getRefinementDuration()).thenReturn(Duration.ofDays(10));

        // Act
        StepVerifier.create(finalEventBuilder.buildFinalEvent(paperTrackings, finalEvent, handlerContext))
                .verifyComplete();

        // Assert
        Assertions.assertEquals(1, handlerContext.getEventsToSend().size());
        Assertions.assertEquals(RECRN003C.name(), handlerContext.getEventsToSend().getFirst().getStatusDetail());
        Assertions.assertEquals(StatusCodeEnum.valueOf(RECRN003C.getStatus().name()), handlerContext.getEventsToSend().getFirst().getStatusCode());
    }

    @Test
    void buildFinalEvent_stockStatus_differenceGreater_enableTruncatedDate_RECRN004C() {
        // Arrange
        Instant statusTimestamp = Instant.now().minus(13, ChronoUnit.DAYS);
        setValidatedEvents(RECRN004A.name(), statusTimestamp);
        PaperProgressStatusEvent finalEvent = getFinalEvent(RECRN004C.name());
        when(cfg.getRefinementDuration()).thenReturn(Duration.ofDays(10));
        when(cfg.isEnableTruncatedDateForRefinementCheck()).thenReturn(true);

        // Act
        StepVerifier.create(finalEventBuilder.buildFinalEvent(paperTrackings, finalEvent, handlerContext))
                .verifyComplete();

        // Assert
        Assertions.assertEquals(2, handlerContext.getEventsToSend().size());
        Assertions.assertEquals(PNRN012.name(), handlerContext.getEventsToSend().getFirst().getStatusDetail());
        Assertions.assertNotNull(handlerContext.getEventsToSend().getFirst().getStatusDateTime());
        Assertions.assertEquals(StatusCodeEnum.PROGRESS, handlerContext.getEventsToSend().getLast().getStatusCode());
    }

    @Test
    void buildFinalEvent_stockStatusFalse() {
        // Arrange
        PaperProgressStatusEvent finalEvent = getFinalEvent(RECRN002F.name());

        // Act
        StepVerifier.create(finalEventBuilder.buildFinalEvent(paperTrackings, finalEvent, handlerContext))
                .verifyComplete();

        // Assert
        Assertions.assertEquals(1, handlerContext.getEventsToSend().size());
        Assertions.assertEquals(RECRN002F.name(), handlerContext.getEventsToSend().getFirst().getStatusDetail());
    }

    private void setValidatedEvents(String statusCode, Instant statusTimestamp) {
        Event event = new Event();
        event.setRequestTimestamp(Instant.now());
        event.setStatusCode(statusCode);
        event.setStatusTimestamp(Instant.now());
        event.setProductType(ProductType.AR);
        Attachment attachment = new Attachment();
        attachment.setId("attachment-id-1");
        attachment.setDocumentType("DOCUMENT_TYPE");
        attachment.setUrl("http://example.com/document.pdf");
        attachment.setDate(Instant.now());
        event.setAttachments(List.of(attachment));
        Event event2 = new Event();
        event2.setRequestTimestamp(Instant.now());
        event2.setStatusCode(RECRN010.name());
        event2.setStatusTimestamp(statusTimestamp);
        List<Event> validatedEvents = List.of(event, event2);
        paperTrackings.getNotificationState().setValidatedEvents(validatedEvents);
    }

    private PaperProgressStatusEvent getFinalEvent(String statusCode) {
        PaperProgressStatusEvent finalEvent = new PaperProgressStatusEvent();
        finalEvent.setStatusCode(statusCode);
        finalEvent.setRequestId("req-123");
        finalEvent.setClientRequestTimeStamp(OffsetDateTime.now());
        finalEvent.setRegisteredLetterCode("RL123");
        finalEvent.setDeliveryFailureCause("M02");
        finalEvent.setAttachments(new ArrayList<>());
        finalEvent.setStatusDateTime(OffsetDateTime.now());
        finalEvent.setProductType("AR");
        return finalEvent;
    }
}
