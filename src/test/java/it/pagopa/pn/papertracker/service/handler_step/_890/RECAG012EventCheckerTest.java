package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RECAG012EventCheckerTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @Mock
    private PnPaperTrackerConfigs configs;

    @InjectMocks
    private RECAG012EventChecker recag012EventChecker;

    private HandlerContext context;

    @BeforeEach
    void setUp() {
        context = new HandlerContext();
        context.setTrackingId("trackingId");
        PaperTrackings paperTrackings = new PaperTrackings();
        Attachment attachment1 = new Attachment();
        attachment1.setId("id1");
        attachment1.setDocumentType("CAD");
        attachment1.setDate(Instant.now());
        attachment1.setUri("uri1");
        Event event1 = new Event();
        event1.setStatusCode("RECAG012");
        event1.setAttachments(List.of(attachment1));
        event1.setRequestTimestamp(Instant.now());
        Attachment attachment2 = new Attachment();
        attachment2.setDocumentType("23L");
        Event event2 = new Event();
        event2.setStatusCode("RECAG003A");
        event2.setAttachments(List.of(attachment2));
        Attachment attachment3 = new Attachment();
        attachment3.setDocumentType("Plico");
        Event event3 = new Event();
        event3.setStatusCode("RECAG006C");
        event3.setAttachments(List.of(attachment3));
        paperTrackings.setEvents(List.of(event1, event2, event3));
        context.setPaperTrackings(paperTrackings);
    }

    @Test
    void executeCompletesWhenAllAttachmentsPresentAndEventRECAG012Found() {
        //Arrange
        when(configs.getRequiredDemats()).thenReturn(List.of("23L", "Plico"));

        //Act
        StepVerifier.create(recag012EventChecker.execute(context))
                .verifyComplete();

        //Assert
        ArgumentCaptor<PaperTrackings> paperTrackings = ArgumentCaptor.forClass(PaperTrackings.class);
        verify(paperTrackingsDAO, times(1)).updateItem(eq("trackingId"), paperTrackings.capture());
        assertEquals(1, context.getEventsToSend().size());
        assertEquals(RECAG012.name(), context.getEventsToSend().getFirst().getStatusDetail());
        assertTrue(paperTrackings.getValue().isRefined());
    }

    @Test
    void executeCompletesWhenAttachmentsMissing() {
        //Arrange
        when(configs.getRequiredDemats()).thenReturn(List.of("ARCAD"));

        //Act
        StepVerifier.create(recag012EventChecker.execute(context))
                .verifyComplete();

        //Assert
        verifyNoInteractions(paperTrackingsDAO);
        assertEquals(0, context.getEventsToSend().size());
    }

    @Test
    void executeCompletesWhenEventRECAG012NotFound() {
        //Arrange
        when(configs.getRequiredDemats()).thenReturn(List.of("CAD"));
        context.getPaperTrackings().getEvents().getFirst().setStatusCode("RECAG015");

        //Act
        StepVerifier.create(recag012EventChecker.execute(context))
                .verifyComplete();

        //Assert
        verifyNoInteractions(paperTrackingsDAO);
        assertEquals(0, context.getEventsToSend().size());
    }
}
