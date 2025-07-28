package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
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
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);

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
                    assert retrieved.getState() == PaperTrackingsState.AWAITING_FINAL_STATUS_CODE;
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
        NotificationState notificationState = new NotificationState();
        notificationState.setDeliveryFailureCause("M02");
        ValidationFlow validationFlow = new ValidationFlow();
        paperTrackings.setNotificationState(notificationState);
        paperTrackings.setValidationFlow(validationFlow);
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
        String ocrRequestId = "test-ocr-request-id";
        paperTrackingsToUpdate.setOcrRequestId(ocrRequestId);
        paperTrackingsToUpdate.setHasNextPcretry(true);
        paperTrackingsToUpdate.setState(PaperTrackingsState.DONE);
        ValidationFlow validationFlow1 = new ValidationFlow();
        validationFlow1.setOcrEnabled(true);
        validationFlow1.setSequencesValidationTimestamp(Instant.now());
        paperTrackingsToUpdate.setValidationFlow(validationFlow1);
        Attachment attachment = new Attachment();
        attachment.setId("attachment-id-1");
        attachment.setDocumentType("DOCUMENT_TYPE");
        Event event = new Event();
        event.setRequestTimestamp(Instant.now());
        event.setStatusCode("RECRN004C");
        event.setStatusTimestamp(Instant.now());
        event.setProductType(ProductType.AR);
        event.setAttachments(List.of(attachment));
        NotificationState notificationState1 = new NotificationState();
        notificationState1.setFinalStatusCode("RECRN005C");
        notificationState1.setDiscoveredAddress("address discovered");
        notificationState1.setValidatedEvents(List.of(event));
        paperTrackingsToUpdate.setNotificationState(notificationState1);

        paperTrackingsDAO.updateItem(requestId, paperTrackingsToUpdate)
                .doOnNext(paperTrackingsUpdated -> {
                    assert paperTrackingsUpdated != null;
                    assert paperTrackingsUpdated.getOcrRequestId().equals(ocrRequestId);
                    assert paperTrackingsUpdated.getProductType() == ProductType.AR;
                    assert paperTrackingsUpdated.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE");
                    assert paperTrackingsUpdated.getHasNextPcretry() == Boolean.TRUE;
                    assert paperTrackingsUpdated.getState() == PaperTrackingsState.DONE;
                    assert paperTrackingsUpdated.getEvents() == null;
                    assert paperTrackingsUpdated.getValidationFlow() != null;
                    assert paperTrackingsUpdated.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert paperTrackingsUpdated.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert paperTrackingsUpdated.getNotificationState() != null;
                    assert paperTrackingsUpdated.getNotificationState().getFinalStatusCode().equals("RECRN005C");
                    assert paperTrackingsUpdated.getNotificationState().getDeliveryFailureCause().equals("M02");
                    assert paperTrackingsUpdated.getNotificationState().getDiscoveredAddress().equals("address discovered");
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents() != null;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().size() == 1;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getStatusCode().equals("RECRN004C");
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getStatusTimestamp() != null;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getProductType().equals(ProductType.AR);
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getAttachments() != null;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getAttachments().size() == 1;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getAttachments().getFirst().getId().equals("attachment-id-1");
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getAttachments().getFirst().getDocumentType().equals("DOCUMENT_TYPE");
                })
                .block();

        // Then
        paperTrackingsDAO.retrieveEntityByOcrRequestId(ocrRequestId)
                .doOnNext(retrieved -> {
                    assert retrieved != null;
                    assert retrieved.getOcrRequestId().equals(ocrRequestId);
                    assert retrieved.getProductType() == ProductType.AR;
                    assert retrieved.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE");
                    assert retrieved.getHasNextPcretry() == Boolean.TRUE;
                    assert retrieved.getState() == PaperTrackingsState.DONE;
                    assert retrieved.getEvents() == null;
                    assert retrieved.getValidationFlow() != null;
                    assert retrieved.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert retrieved.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert retrieved.getNotificationState() != null;
                    assert retrieved.getNotificationState().getFinalStatusCode().equals("RECRN005C");
                    assert retrieved.getNotificationState().getDeliveryFailureCause().equals("M02");
                    assert retrieved.getNotificationState().getDiscoveredAddress().equals("address discovered");
                    assert retrieved.getNotificationState().getValidatedEvents() != null;
                    assert retrieved.getNotificationState().getValidatedEvents().size() == 1;
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getStatusCode().equals("RECRN004C");
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getStatusTimestamp() != null;
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getProductType().equals(ProductType.AR);
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getAttachments() != null;
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getAttachments().size() == 1;
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getAttachments().getFirst().getId().equals("attachment-id-1");
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getAttachments().getFirst().getDocumentType().equals("DOCUMENT_TYPE");
                })
                .blockLast();

        PaperTrackings updateDematValidationTimestamp = new PaperTrackings();
        ValidationFlow validationFlow2 = new ValidationFlow();
        validationFlow2.setDematValidationTimestamp(Instant.now());
        updateDematValidationTimestamp.setValidationFlow(validationFlow2);

        paperTrackingsDAO.updateItem(requestId, updateDematValidationTimestamp)
                .doOnNext(paperTrackingsUpdated -> {
                    assert paperTrackingsUpdated != null;
                    assert paperTrackingsUpdated.getOcrRequestId().equals(ocrRequestId);
                    assert paperTrackingsUpdated.getProductType() == ProductType.AR;
                    assert paperTrackingsUpdated.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE");
                    assert paperTrackingsUpdated.getHasNextPcretry() == Boolean.TRUE;
                    assert paperTrackingsUpdated.getState() == PaperTrackingsState.DONE;
                    assert paperTrackingsUpdated.getEvents() == null;
                    assert paperTrackingsUpdated.getValidationFlow() != null;
                    assert paperTrackingsUpdated.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert paperTrackingsUpdated.getValidationFlow().getDematValidationTimestamp() != null;
                    assert paperTrackingsUpdated.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert paperTrackingsUpdated.getNotificationState() != null;
                    assert paperTrackingsUpdated.getNotificationState().getFinalStatusCode().equals("RECRN005C");
                    assert paperTrackingsUpdated.getNotificationState().getDeliveryFailureCause().equals("M02");
                    assert paperTrackingsUpdated.getNotificationState().getDiscoveredAddress().equals("address discovered");
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents() != null;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().size() == 1;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getStatusCode().equals("RECRN004C");
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getStatusTimestamp() != null;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getProductType().equals(ProductType.AR);
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getAttachments() != null;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getAttachments().size() == 1;
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getAttachments().getFirst().getId().equals("attachment-id-1");
                    assert paperTrackingsUpdated.getNotificationState().getValidatedEvents().getFirst().getAttachments().getFirst().getDocumentType().equals("DOCUMENT_TYPE");
                })
                .block();

        paperTrackingsDAO.retrieveEntityByOcrRequestId(ocrRequestId)
                .doOnNext(retrieved -> {
                    assert retrieved != null;
                    assert retrieved.getOcrRequestId().equals(ocrRequestId);
                    assert retrieved.getProductType() == ProductType.AR;
                    assert retrieved.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE");
                    assert retrieved.getHasNextPcretry() == Boolean.TRUE;
                    assert retrieved.getState() == PaperTrackingsState.DONE;
                    assert retrieved.getEvents() == null;
                    assert retrieved.getValidationFlow() != null;
                    assert retrieved.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert retrieved.getValidationFlow().getDematValidationTimestamp() != null;
                    assert retrieved.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert retrieved.getNotificationState() != null;
                    assert retrieved.getNotificationState().getFinalStatusCode().equals("RECRN005C");
                    assert retrieved.getNotificationState().getDeliveryFailureCause().equals("M02");
                    assert retrieved.getNotificationState().getDiscoveredAddress().equals("address discovered");
                    assert retrieved.getNotificationState().getValidatedEvents() != null;
                    assert retrieved.getNotificationState().getValidatedEvents().size() == 1;
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getStatusCode().equals("RECRN004C");
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getStatusTimestamp() != null;
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getProductType().equals(ProductType.AR);
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getAttachments() != null;
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getAttachments().size() == 1;
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getAttachments().getFirst().getId().equals("attachment-id-1");
                    assert retrieved.getNotificationState().getValidatedEvents().getFirst().getAttachments().getFirst().getDocumentType().equals("DOCUMENT_TYPE");
                })
                .blockLast();
    }

    @Test
    void updateItemRequestIdNotExists() {
        String requestId = "test-request-id";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId(requestId);

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
        String ocrRequestId = "test-ocr-request-id";
        paperTrackingsToUpdate.setOcrRequestId(ocrRequestId);

        StepVerifier.create(paperTrackingsDAO.updateItem("non-existing-request-id", paperTrackingsToUpdate))
                .expectError(PnPaperTrackerNotFoundException.class)
                .verify();
    }

    @Test
    void addEvents() {
        String requestId = "test-request-id-3";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId(requestId);
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
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
        paperTrackingsToUpdate.setEvents(List.of(event));

        paperTrackingsDAO.updateItem(requestId, paperTrackingsToUpdate).block();

        // Then
        PaperTrackings fisrtResponse = paperTrackingsDAO.retrieveEntityByRequestId(requestId).block();
        Assertions.assertNotNull(fisrtResponse);
        Assertions.assertNotNull(fisrtResponse.getEvents());
        Assertions.assertNotNull(fisrtResponse.getUpdatedAt());
        Assertions.assertFalse(fisrtResponse.getEvents().isEmpty());
        Assertions.assertEquals(1, fisrtResponse.getEvents().size());
        Assertions.assertEquals(2, fisrtResponse.getEvents().getFirst().getAttachments().size());
        Assertions.assertNull(fisrtResponse.getEvents().getFirst().getDeliveryFailureCause());

        PaperTrackings paperTrackingsToUpdate1 = new PaperTrackings();
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
        paperTrackingsToUpdate1.setEvents(List.of(event2));

        paperTrackingsDAO.updateItem(requestId, paperTrackingsToUpdate1).block();

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
