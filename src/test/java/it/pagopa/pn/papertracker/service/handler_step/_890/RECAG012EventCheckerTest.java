package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ValidationConfig;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RECAG012EventCheckerTest {

    @Mock
    private OcrUtility ocrUtility;

    @InjectMocks
    private RECAG012EventChecker recag012EventChecker;

    private HandlerContext context;

    @BeforeEach
    void setUp() {
        context = new HandlerContext();
        context.setTrackingId("trackingId");
        context.setEventId("id_RECAG012");
        PaperTrackings paperTrackings = new PaperTrackings();
        Attachment attachment1 = new Attachment();
        attachment1.setId("id1");
        attachment1.setDocumentType("CAD");
        attachment1.setDate(Instant.now());
        attachment1.setUri("uri1");
        Event event1 = new Event();
        event1.setId("id_RECAG012");
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
        ValidationConfig validationConfig = new ValidationConfig();
        paperTrackings.setValidationConfig(validationConfig);
        context.setPaperTrackings(paperTrackings);
    }

    @Test
    void executeCompletesWhenAllAttachmentsPresentAndEventRECAG012Found() {
        //Arrange
        context.getPaperTrackings().getValidationConfig().setRequiredAttachmentsRefinementStock890(List.of(DocumentTypeEnum.PLICO.getValue(), DocumentTypeEnum._23L.getValue(), DocumentTypeEnum.CAD.getValue()));
        when(ocrUtility.checkAndSendToOcr(context.getPaperTrackings().getEvents().getFirst(), context.getPaperTrackings().getValidationConfig().getRequiredAttachmentsRefinementStock890(), context)).thenReturn(Mono.empty());

        //Act
        StepVerifier.create(recag012EventChecker.execute(context))
                .verifyComplete();

        //Assert
        verify(ocrUtility, times(1)).checkAndSendToOcr(context.getPaperTrackings().getEvents().getFirst(), context.getPaperTrackings().getValidationConfig().getRequiredAttachmentsRefinementStock890(), context);
        assertTrue(context.isRefinementCondition());
    }

    @Test
    void executeCompletesWhenAttachmentsMissing() {
        //Arrange
        context.getPaperTrackings().getValidationConfig().setRequiredAttachmentsRefinementStock890(List.of(DocumentTypeEnum.ARCAD.getValue()));

        //Act
        StepVerifier.create(recag012EventChecker.execute(context))
                .verifyComplete();

        //Assert
        verifyNoInteractions(ocrUtility);
        assertFalse(context.isRefinementCondition());
    }

    @Test
    void executeCompletesWhenEventRECAG012NotFound() {
        //Arrange
        context.getPaperTrackings().getValidationConfig().setRequiredAttachmentsRefinementStock890(List.of(DocumentTypeEnum.CAD.getValue()));
        context.getPaperTrackings().getEvents().getFirst().setStatusCode("RECAG015");

        //Act
        StepVerifier.create(recag012EventChecker.execute(context))
                .verifyComplete();

        //Assert
        verifyNoInteractions(ocrUtility);
        assertFalse(context.isRefinementCondition());
    }
}
