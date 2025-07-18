package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

public class PaperTrackingsDaoIT extends BaseTest.WithLocalStack {

    @Autowired
    PaperTrackingsDAO paperTrackingsDAO;

    @Test
    void putIfAbsentAndRetrieveByRequestId() {
        String requestId = "test-request-id-1";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId(requestId);
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        // Then
        paperTrackingsDAO.retrieveEntityByRequestId(requestId)
                .doOnNext(retrieved -> {
                    assert retrieved != null;
                    assert retrieved.getRequestId().equals(requestId);
                    assert retrieved.getProductType() == ProductType.AR;
                    assert "POSTE".equalsIgnoreCase(retrieved.getUnifiedDeliveryDriver());
                    assert retrieved.getEvents() == null;
                    assert retrieved.getValidationFlow() == null;
                    assert retrieved.getOcrRequestId() == null;
                    assert retrieved.getHasNextPcretry() == null;
                })
                .block();

        StepVerifier.create(paperTrackingsDAO.putIfAbsent(paperTrackings))
                .expectError(PnPaperTrackerConflictException.class)
                .verify();
    }

    @Test
    void updateItemAndRetrieveByOcrRequestId() {
        String requestId = "test-request-id-2";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId(requestId);
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
        String ocrRequestId = "test-ocr-request-id";
        paperTrackingsToUpdate.setOcrRequestId(ocrRequestId);
        paperTrackingsToUpdate.setRequestId(requestId);
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setOcrEnabled(true);
        validationFlow.setSequencesValidationTimestamp(Instant.now());
        paperTrackingsToUpdate.setValidationFlow(validationFlow);
        NotificationState notificationState = new NotificationState();
        notificationState.setFinalStatusCode("RECRN005C");
        paperTrackingsToUpdate.setNotificationState(notificationState);

        paperTrackingsDAO.updateItem(paperTrackingsToUpdate).block();

        // Then
        paperTrackingsDAO.retrieveEntityByOcrRequestId(ocrRequestId)
                .doOnNext(retrieved -> {
                    assert retrieved != null;
                    assert retrieved.getOcrRequestId().equals(ocrRequestId);
                    assert retrieved.getProductType() == ProductType.AR;
                    assert retrieved.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE");
                    assert retrieved.getEvents() == null;
                    assert retrieved.getValidationFlow() != null;
                    assert retrieved.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert retrieved.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert retrieved.getNotificationState() != null;
                    assert retrieved.getNotificationState().getFinalStatusCode().equals("RECRN005C");
                })
                .blockLast();

        PaperTrackings updateDematValidationTimestamp = new PaperTrackings();
        updateDematValidationTimestamp.setRequestId(requestId);
        ValidationFlow validationFlow2 = new ValidationFlow();
        validationFlow2.setDematValidationTimestamp(Instant.now());
        updateDematValidationTimestamp.setValidationFlow(validationFlow2);

        paperTrackingsDAO.updateItem(updateDematValidationTimestamp).block();

        paperTrackingsDAO.retrieveEntityByOcrRequestId(ocrRequestId)
                .doOnNext(retrieved -> {
                    assert retrieved != null;
                    assert retrieved.getOcrRequestId().equals(ocrRequestId);
                    assert retrieved.getProductType() == ProductType.AR;
                    assert retrieved.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE");
                    assert retrieved.getEvents() == null;
                    assert retrieved.getValidationFlow() != null;
//                    assert retrieved.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert retrieved.getValidationFlow().getDematValidationTimestamp() != null;
//                    assert retrieved.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert retrieved.getNotificationState() != null;
                    assert retrieved.getNotificationState().getFinalStatusCode().equals("RECRN005C");
                })
                .blockLast();
    }

    @Test
    void addEvents() {
        String requestId = "test-request-id-3";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId(requestId);
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        Event event = new Event();
        event.setRequestTimestamp(Instant.now());
        event.setStatusCode("IN_PROGRESS");
        event.setStatusTimestamp(Instant.now());
        event.setProductType(ProductType.AR);

        Attachment attachment = new Attachment();
        attachment.setId("attachment-id-1");
        attachment.setDocumentType("DOCUMENT_TYPE");
        attachment.setUrl("http://example.com/document.pdf");
        attachment.setDate(Instant.now());

        Attachment attachment2 = new Attachment();
        attachment2.setId("attachment-id-1");
        attachment2.setDocumentType("DOCUMENT_TYPE");
        attachment2.setUrl("http://example.com/document.pdf");

        event.setAttachments(List.of(attachment, attachment2));

        paperTrackingsDAO.addEvents(requestId, event).block();

        // Then
        PaperTrackings fisrtResponse = paperTrackingsDAO.retrieveEntityByRequestId(requestId).block();
        Assertions.assertNotNull(fisrtResponse);
        Assertions.assertNotNull(fisrtResponse.getEvents());
        Assertions.assertNotNull(fisrtResponse.getUpdatedAt());
        Assertions.assertFalse(fisrtResponse.getEvents().isEmpty());
        Assertions.assertEquals(1, fisrtResponse.getEvents().size());
        Assertions.assertEquals(2, fisrtResponse.getEvents().getFirst().getAttachments().size());


        Event event2 = new Event();
        event2.setRequestTimestamp(Instant.now());
        event2.setStatusCode("IN_PROGRESS");
        event2.setStatusTimestamp(Instant.now());

        Attachment attachment3 = new Attachment();
        attachment.setId("attachment-id-1");
        attachment.setDocumentType("DOCUMENT_TYPE");
        attachment.setUrl("http://example.com/document.pdf");
        attachment.setDate(Instant.now());

        event2.setAttachments(List.of(attachment3));

        paperTrackingsDAO.addEvents(requestId, event2).block();

        PaperTrackings secondeResponse = paperTrackingsDAO.retrieveEntityByRequestId(requestId).block();
        Assertions.assertNotNull(secondeResponse);
        Assertions.assertNotNull(secondeResponse.getEvents());
        Assertions.assertNotNull(secondeResponse.getUpdatedAt());
        Assertions.assertNotEquals(fisrtResponse.getUpdatedAt(), secondeResponse.getUpdatedAt());
        Assertions.assertFalse(secondeResponse.getEvents().isEmpty());
        Assertions.assertEquals(2, secondeResponse.getEvents().size());
        Assertions.assertEquals(2, secondeResponse.getEvents().getFirst().getAttachments().size());
        Assertions.assertEquals(1, secondeResponse.getEvents().getLast().getAttachments().size());
    }

}
