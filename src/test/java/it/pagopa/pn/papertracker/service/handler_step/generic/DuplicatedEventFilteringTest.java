package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class DuplicatedEventFilteringTest {

    @InjectMocks
    private DuplicatedEventFiltering duplicatedEventFiltering;

    private HandlerContext context;

    @BeforeEach
    void setup(){
        context = new HandlerContext();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setState(PaperTrackingsState.DONE);
        Attachment attachment = new Attachment();
        attachment.setId("attachment-id-1");
        attachment.setDocumentType("DOCUMENT_TYPE");
        Event event = new Event();
        event.setRequestTimestamp(Instant.now());
        event.setStatusCode("RECRN004C");
        event.setStatusTimestamp(Instant.now());
        event.setProductType(ProductType.AR);
        event.setAttachments(List.of(attachment));
        paperTrackings.setEvents(List.of(event));
        context.setPaperTrackings(paperTrackings);
    }

    @Test
    void duplicateEvent() {
        PaperProgressStatusEvent newEvent = new PaperProgressStatusEvent();
        newEvent.setStatusCode("RECRN004C");
        newEvent.setStatusDateTime(context.getPaperTrackings().getEvents().getFirst().getStatusTimestamp().atOffset(java.time.ZoneOffset.UTC));
        newEvent.setProductType(ProductType.AR.getValue());
        AttachmentDetails attachmentDetails = new AttachmentDetails();
        attachmentDetails.setId("attachment-id-1");
        attachmentDetails.setDocumentType("DOCUMENT_TYPE");
        newEvent.setAttachments(List.of(attachmentDetails));
        context.setPaperProgressStatusEvent(newEvent);

        StepVerifier.create(duplicatedEventFiltering.execute(context))
                .verifyError(PnPaperTrackerValidationException.class);
    }

    @Test
    void sameEventWithDifferentField() {
        PaperProgressStatusEvent newEvent = new PaperProgressStatusEvent();
        newEvent.setStatusCode("RECRN004C");
        newEvent.setStatusDateTime(context.getPaperTrackings().getEvents().getFirst().getStatusTimestamp().atOffset(java.time.ZoneOffset.UTC));
        newEvent.setProductType(ProductType.AR.getValue());
        AttachmentDetails attachmentDetails = new AttachmentDetails();
        attachmentDetails.setId("attachment-id-1");
        attachmentDetails.setDocumentType("DOCUMENT_TYPE2");
        newEvent.setAttachments(List.of(attachmentDetails));
        context.setPaperProgressStatusEvent(newEvent);
        StepVerifier.create(duplicatedEventFiltering.execute(context))
                .verifyComplete();
    }

    @Test
    void newEvent() {
        PaperProgressStatusEvent newEvent = new PaperProgressStatusEvent();
        newEvent.setStatusCode("RECRN004B");
        newEvent.setStatusDateTime(context.getPaperTrackings().getEvents().getFirst().getStatusTimestamp().atOffset(java.time.ZoneOffset.UTC));
        newEvent.setProductType(ProductType.AR.getValue());
        AttachmentDetails attachmentDetails = new AttachmentDetails();
        attachmentDetails.setId("attachment-id-1");
        attachmentDetails.setDocumentType("DOCUMENT_TYPE2");
        newEvent.setAttachments(List.of(attachmentDetails));
        context.setPaperProgressStatusEvent(newEvent);
        StepVerifier.create(duplicatedEventFiltering.execute(context))
                .verifyComplete();
    }
}
