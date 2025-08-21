package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static it.pagopa.pn.papertracker.config.StatusCodeConfiguration.StatusCodeConfigurationEnum.RECRI002;
import static it.pagopa.pn.papertracker.config.StatusCodeConfiguration.StatusCodeConfigurationEnum.RECRI003C;

@ExtendWith(MockitoExtension.class)
class GenericFinalEventBuilderTest {

    @Mock
    DataVaultClient dataVaultClient;

    GenericFinalEventBuilder genericFinalEventBuilder;

    HandlerContext handlerContext;

    PaperTrackings paperTrackings;

    StatusCodeConfiguration statusCodeConfiguration;

    private static final String EVENT_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        handlerContext = new HandlerContext();
        handlerContext.setTrackingId("req-123");
        genericFinalEventBuilder = new GenericFinalEventBuilder(dataVaultClient);
        paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId("req-123");
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.getPaperStatus().setRegisteredLetterCode("RL123");
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setSequencesValidationTimestamp(Instant.now());
        paperTrackings.setValidationFlow(validationFlow);
        Event event = new Event();
        event.setStatusCode("RECRI001");
        event.setStatusTimestamp(now);
        event.setRequestTimestamp(now);
        event.setId(EVENT_ID + "1");

        Event event1 = new Event();
        event1.setStatusCode("RECRI002");
        event1.setStatusTimestamp(now);
        event1.setRequestTimestamp(now);
        event1.setId(EVENT_ID + "2");

        Event event2 = new Event();
        event2.setStatusCode("RECRI003C");
        event2.setStatusTimestamp(now);
        event2.setRequestTimestamp(now);
        event2.setId(EVENT_ID);

        paperTrackings.setEvents(List.of(event, event1, event2));
        handlerContext.setPaperTrackings(paperTrackings);
    }

    @Test
    void genericFinalEvent_RECRI003C() {
        // Arrange
        handlerContext.setPaperProgressStatusEvent(getFinalEvent("RECRI003C"));
        handlerContext.setEventId(EVENT_ID);

        // Act
        StepVerifier.create(genericFinalEventBuilder.execute(handlerContext))
                .verifyComplete();

        // Assert
        Assertions.assertEquals(1, handlerContext.getEventsToSend().size());
        Assertions.assertEquals(RECRI003C.name(), handlerContext.getEventsToSend().getFirst().getStatusDetail());
        Assertions.assertEquals(StatusCodeEnum.OK, handlerContext.getEventsToSend().getFirst().getStatusCode());
    }

    @Test
    void genericFinalEvent_RECRI002() {
        // Arrange
        handlerContext.setPaperProgressStatusEvent(getFinalEvent("RECRI002"));
        handlerContext.setEventId(EVENT_ID + "2");

        // Act
        StepVerifier.create(genericFinalEventBuilder.execute(handlerContext))
                .verifyComplete();

        // Assert
        Assertions.assertEquals(1, handlerContext.getEventsToSend().size());
        Assertions.assertEquals(RECRI002.name(), handlerContext.getEventsToSend().getFirst().getStatusDetail());
        Assertions.assertEquals(StatusCodeEnum.PROGRESS, handlerContext.getEventsToSend().getFirst().getStatusCode());
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
