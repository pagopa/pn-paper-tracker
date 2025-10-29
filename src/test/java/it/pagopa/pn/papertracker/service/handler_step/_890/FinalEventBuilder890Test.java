package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
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
import java.util.List;
import java.util.UUID;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FinalEventBuilder890Test {

    @Mock
    private DataVaultClient dataVaultClient;

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    private HandlerContext context;

    private PaperTrackings paperTrackings;

    private FinalEventBuilder890 finalEventBuilder890;

    private static final String EVENT_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        context = new HandlerContext();
        context.setTrackingId("req-123");
        context.setEventId(EVENT_ID);
        finalEventBuilder890 = new FinalEventBuilder890(dataVaultClient, paperTrackingsDAO);
        paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId("req-123");
        paperTrackings.setProductType(ProductType._890);
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.getPaperStatus().setRegisteredLetterCode("RL123");
        paperTrackings.getPaperStatus().setDeliveryFailureCause("M08");

        Event event = new Event();
        event.setStatusTimestamp(now);
        event.setRequestTimestamp(now);
        event.setId(EVENT_ID);

        Event event1 = new Event();
        event1.setStatusCode("RECAG005A");
        event1.setStatusTimestamp(now);
        event1.setRequestTimestamp(now);
        event1.setId(EVENT_ID + "1");

        paperTrackings.setEvents(List.of(event, event1));
        context.setPaperTrackings(paperTrackings);
    }

    @Test
    void executeWithStockStatusAndRefined() {
        context.getPaperTrackings().getEvents().getFirst().setStatusCode("RECAG005C");
        context.getPaperTrackings().setRefined(true);

        StepVerifier.create(finalEventBuilder890.execute(context))
                .verifyComplete();

        // Assert
        assertEquals(1, context.getEventsToSend().size());
        assertEquals(RECAG005C.name(), context.getEventsToSend().getFirst().getStatusDetail());
        assertEquals("M08", context.getEventsToSend().getFirst().getDeliveryFailureCause());
        assertNull(context.getEventsToSend().getFirst().getDiscoveredAddress());
        assertEquals(StatusCodeEnum.PROGRESS, context.getEventsToSend().getFirst().getStatusCode());
    }

    @Test
    void executeWithStockStatusAndNotRefined() {
        context.getPaperTrackings().getEvents().getFirst().setStatusCode("RECAG007C");
        context.getPaperTrackings().setRefined(false);

        StepVerifier.create(finalEventBuilder890.execute(context))
                .expectErrorSatisfies(throwable -> {
                    Assertions.assertInstanceOf(PnPaperTrackerValidationException.class, throwable);
                    PnPaperTrackerValidationException ex = (PnPaperTrackerValidationException) throwable;
                    Assertions.assertNotNull(ex.getError());
                    Assertions.assertEquals(paperTrackings.getTrackingId(), ex.getError().getTrackingId());
                    Assertions.assertEquals("RECAG007C", ex.getError().getEventThrow());
                    Assertions.assertEquals(ProductType._890, ex.getError().getProductType());
                    Assertions.assertEquals(ErrorCause.GIACENZA_RECAG012_ERROR, ex.getError().getDetails().getCause());
                })
                .verify();

        assertTrue(context.getEventsToSend().isEmpty());
    }

    @Test
    void executeWithNonStockStatusButRECAG003C() {
        context.getPaperTrackings().getEvents().getFirst().setStatusCode("RECAG003C");

        StepVerifier.create(finalEventBuilder890.execute(context))
                .verifyComplete();

        // Assert
        assertEquals(1, context.getEventsToSend().size());
        assertEquals(RECAG003C.name(), context.getEventsToSend().getFirst().getStatusDetail());
        assertEquals("M08", context.getEventsToSend().getFirst().getDeliveryFailureCause());
        assertNull(context.getEventsToSend().getFirst().getDiscoveredAddress());
        assertEquals(StatusCodeEnum.KO, context.getEventsToSend().getFirst().getStatusCode());
    }

    @Test
    void executeWithNonStockStatus() {
        context.getPaperTrackings().getEvents().getFirst().setStatusCode("RECAG001C");

        StepVerifier.create(finalEventBuilder890.execute(context))
                .verifyComplete();

        // Assert
        assertEquals(1, context.getEventsToSend().size());
        assertEquals(RECAG001C.name(), context.getEventsToSend().getFirst().getStatusDetail());
        assertEquals("M08", context.getEventsToSend().getFirst().getDeliveryFailureCause());
        assertNull(context.getEventsToSend().getFirst().getDiscoveredAddress());
        assertEquals(StatusCodeEnum.OK, context.getEventsToSend().getFirst().getStatusCode());
    }
}
