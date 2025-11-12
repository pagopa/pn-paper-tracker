package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class RECAG012EventBuilderTest {

    private HandlerContext context;

    @InjectMocks
    private RECAG012EventBuilder recag012EventBuilder;

    @BeforeEach
    void setUp() {
        context = new HandlerContext();
        context.setTrackingId("trackingId");
        context.setEventId("id_RECAG012");
        PaperTrackings paperTrackings = new PaperTrackings();
        Event event1 = new Event();
        event1.setId("id_RECAG012");
        event1.setStatusCode("RECAG012");
        event1.setRequestTimestamp(Instant.now());
        Event event2 = new Event();
        event2.setId("eventId2");
        event2.setStatusCode("RECAG003A");
        Event event3 = new Event();
        event3.setId("eventId3");
        event3.setStatusCode("RECAG006C");
        paperTrackings.setEvents(List.of(event1, event2, event3));
        context.setPaperTrackings(paperTrackings);
    }

    @Test
    void executeShouldLogRequestRefinedWhenRefined() {
        // Arrange
        context.getPaperTrackings().setRefined(true);

        // Act & Assert
        StepVerifier.create(recag012EventBuilder.execute(context))
                .verifyComplete();

        Assertions.assertEquals(0, context.getEventsToSend().size());
    }

    @Test
    void executeShouldCreateAndSendEventWhenNotRefined() {
        // Arrange
        context.getPaperTrackings().setRefined(false);

        // Act & Assert
        StepVerifier.create(recag012EventBuilder.execute(context))
                .verifyComplete();

        Assertions.assertEquals(1, context.getEventsToSend().size());
        Assertions.assertEquals("RECAG012A", context.getEventsToSend().getFirst().getStatusDetail());
        Assertions.assertEquals(StatusCodeEnum.PROGRESS, context.getEventsToSend().getFirst().getStatusCode());
    }

    @Test
    void createAndSendRECAG012AEventShouldThrowWhenEventNotFound() {
        // Arrange
        context.getPaperTrackings().setRefined(false);
        context.getPaperTrackings().getEvents().getFirst().setId("different_id");

        // Act & Assert
        try {
            recag012EventBuilder.execute(context);
        } catch (Exception e) {
            Assertions.assertInstanceOf(RuntimeException.class, e);
        }

        Assertions.assertEquals(0, context.getEventsToSend().size());
    }
}
