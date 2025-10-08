package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;

public class PaperTrackingsDaoIT extends BaseTest.WithLocalStack {

    @Autowired
    PaperTrackingsDAO paperTrackingsDAO;

    @Test
    void putIfAbsentAndRetrieveByRequestId() {
        //Arrange
        String requestId = "test-request-id-1";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(requestId);
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        //Assert
        paperTrackingsDAO.retrieveEntityByTrackingId(requestId)
                .doOnNext(retrieved -> {
                    assert retrieved != null;
                    assert retrieved.getTrackingId().equals(requestId);
                    assert retrieved.getProductType() == ProductType.AR;
                    assert "POSTE".equalsIgnoreCase(retrieved.getUnifiedDeliveryDriver());
                    assert retrieved.getEvents() == null;
                    assert retrieved.getValidationFlow() == null;
                    assert retrieved.getOcrRequestId() == null;
                    assert retrieved.getNextRequestIdPcretry() == null;
                    assert retrieved.getState() == PaperTrackingsState.AWAITING_FINAL_STATUS_CODE;
                })
                .block();

        StepVerifier.create(paperTrackingsDAO.putIfAbsent(paperTrackings))
                .expectError(PnPaperTrackerConflictException.class)
                .verify();
    }

    @Test
    void retrieveByAttemptId() {
        //Arrange
        String attemptId = "attemptId";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(attemptId + ".PCRETRY_1");
        paperTrackings.setAttemptId(attemptId);
        paperTrackings.setPcRetry("PCRETRY_1");
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        paperTrackings.setState(PaperTrackingsState.KO);

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        PaperTrackings paperTrackings2 = new PaperTrackings();
        paperTrackings2.setTrackingId(attemptId + ".PCRETRY_0");
        paperTrackings2.setAttemptId(attemptId);
        paperTrackings2.setPcRetry("PCRETRY_0");
        paperTrackings2.setProductType(ProductType.AR);
        paperTrackings2.setUnifiedDeliveryDriver("POSTE");
        paperTrackings2.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);

        paperTrackingsDAO.putIfAbsent(paperTrackings2).block();

        PaperTrackings paperTrackings3 = new PaperTrackings();
        paperTrackings3.setTrackingId(attemptId + ".PCRETRY_2");
        paperTrackings3.setAttemptId(attemptId);
        paperTrackings3.setPcRetry("PCRETRY_2");
        paperTrackings3.setProductType(ProductType.AR);
        paperTrackings3.setUnifiedDeliveryDriver("POSTE");
        paperTrackings3.setState(PaperTrackingsState.DONE);

        paperTrackingsDAO.putIfAbsent(paperTrackings3).block();

        List<PaperTrackings> response = paperTrackingsDAO.retrieveEntityByAttemptId(attemptId, null)
                .collectList()
                .block();

        // Assert
        Assertions.assertNotNull(response);
        Assertions.assertEquals(3, response.size());
        Assertions.assertTrue(response.stream().allMatch(track -> track.getAttemptId().equalsIgnoreCase(attemptId)
                && track.getProductType().equals(ProductType.AR) && track.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE")));

        Assertions.assertEquals("PCRETRY_0", response.getFirst().getPcRetry());
        Assertions.assertEquals("PCRETRY_1", response.get(1).getPcRetry());
        Assertions.assertEquals("PCRETRY_2", response.getLast().getPcRetry());
        Assertions.assertEquals(attemptId + ".PCRETRY_0", response.getFirst().getTrackingId());
        Assertions.assertEquals(attemptId + ".PCRETRY_1", response.get(1).getTrackingId());
        Assertions.assertEquals(attemptId + ".PCRETRY_2", response.getLast().getTrackingId());
        Assertions.assertEquals(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE, response.getFirst().getState());
        Assertions.assertEquals(PaperTrackingsState.KO, response.get(1).getState());
        Assertions.assertEquals(PaperTrackingsState.DONE, response.getLast().getState());
    }

    @Test
    void updateItemAndRetrieveByOcrRequestId() {
        //Arrange
        String requestId = "test-request-id-2";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(requestId);
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        PaperStatus notificationState = new PaperStatus();
        notificationState.setDeliveryFailureCause("M02");
        ValidationFlow validationFlow = new ValidationFlow();
        paperTrackings.setPaperStatus(notificationState);
        paperTrackings.setValidationFlow(validationFlow);
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
        String ocrRequestId = "test-ocr-request-id";
        paperTrackingsToUpdate.setOcrRequestId(ocrRequestId);
        paperTrackingsToUpdate.setNextRequestIdPcretry("next-request-id-pcretry");
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
        event.setDryRun(true);
        event.setAttachments(List.of(attachment));
        PaperStatus notificationState1 = new PaperStatus();
        notificationState1.setFinalStatusCode("RECRN005C");
        notificationState1.setAnonymizedDiscoveredAddress("address discovered");
        notificationState1.setValidatedEvents(List.of(event));
        paperTrackingsToUpdate.setPaperStatus(notificationState1);

        paperTrackingsDAO.updateItem(requestId, paperTrackingsToUpdate)
                .doOnNext(paperTrackingsUpdated -> {
                    assert paperTrackingsUpdated != null;
                    assert paperTrackingsUpdated.getOcrRequestId().equals(ocrRequestId);
                    assert paperTrackingsUpdated.getProductType() == ProductType.AR;
                    assert paperTrackingsUpdated.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE");
                    assert paperTrackingsUpdated.getNextRequestIdPcretry().equals("next-request-id-pcretry");
                    assert paperTrackingsUpdated.getState() == PaperTrackingsState.DONE;
                    assert paperTrackingsUpdated.getEvents() == null;
                    assert paperTrackingsUpdated.getValidationFlow() != null;
                    assert paperTrackingsUpdated.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert paperTrackingsUpdated.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert paperTrackingsUpdated.getPaperStatus() != null;
                    assert paperTrackingsUpdated.getPaperStatus().getFinalStatusCode().equals("RECRN005C");
                    assert paperTrackingsUpdated.getPaperStatus().getDeliveryFailureCause().equals("M02");
                    assert paperTrackingsUpdated.getPaperStatus().getAnonymizedDiscoveredAddress().equals("address discovered");
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents() != null;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().size() == 1;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getStatusCode().equals("RECRN004C");
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getStatusTimestamp() != null;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getProductType().equals(ProductType.AR);
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getAttachments() != null;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getAttachments().size() == 1;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getAttachments().getFirst().getId().equals("attachment-id-1");
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getAttachments().getFirst().getDocumentType().equals("DOCUMENT_TYPE");
                })
                .block();

        //Assert
        paperTrackingsDAO.retrieveEntityByOcrRequestId(ocrRequestId)
                .doOnNext(retrieved -> {
                    assert retrieved != null;
                    assert retrieved.getOcrRequestId().equals(ocrRequestId);
                    assert retrieved.getProductType() == ProductType.AR;
                    assert retrieved.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE");
                    assert retrieved.getNextRequestIdPcretry().equals("next-request-id-pcretry");
                    assert retrieved.getState() == PaperTrackingsState.DONE;
                    assert retrieved.getEvents() == null;
                    assert retrieved.getValidationFlow() != null;
                    assert retrieved.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert retrieved.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert retrieved.getPaperStatus() != null;
                    assert retrieved.getPaperStatus().getFinalStatusCode().equals("RECRN005C");
                    assert retrieved.getPaperStatus().getDeliveryFailureCause().equals("M02");
                    assert retrieved.getPaperStatus().getAnonymizedDiscoveredAddress().equals("address discovered");
                    assert retrieved.getPaperStatus().getValidatedEvents() != null;
                    assert retrieved.getPaperStatus().getValidatedEvents().size() == 1;
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getStatusCode().equals("RECRN004C");
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getStatusTimestamp() != null;
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getProductType().equals(ProductType.AR);
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getAttachments() != null;
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getAttachments().size() == 1;
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getAttachments().getFirst().getId().equals("attachment-id-1");
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getAttachments().getFirst().getDocumentType().equals("DOCUMENT_TYPE");
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
                    assert paperTrackingsUpdated.getNextRequestIdPcretry().equals("next-request-id-pcretry");
                    assert paperTrackingsUpdated.getState() == PaperTrackingsState.DONE;
                    assert paperTrackingsUpdated.getEvents() == null;
                    assert paperTrackingsUpdated.getValidationFlow() != null;
                    assert paperTrackingsUpdated.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert paperTrackingsUpdated.getValidationFlow().getDematValidationTimestamp() != null;
                    assert paperTrackingsUpdated.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert paperTrackingsUpdated.getPaperStatus() != null;
                    assert paperTrackingsUpdated.getPaperStatus().getFinalStatusCode().equals("RECRN005C");
                    assert paperTrackingsUpdated.getPaperStatus().getDeliveryFailureCause().equals("M02");
                    assert paperTrackingsUpdated.getPaperStatus().getAnonymizedDiscoveredAddress().equals("address discovered");
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents() != null;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().size() == 1;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getStatusCode().equals("RECRN004C");
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getStatusTimestamp() != null;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getProductType().equals(ProductType.AR);
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getAttachments() != null;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getAttachments().size() == 1;
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getAttachments().getFirst().getId().equals("attachment-id-1");
                    assert paperTrackingsUpdated.getPaperStatus().getValidatedEvents().getFirst().getAttachments().getFirst().getDocumentType().equals("DOCUMENT_TYPE");
                })
                .block();

        paperTrackingsDAO.retrieveEntityByOcrRequestId(ocrRequestId)
                .doOnNext(retrieved -> {
                    assert retrieved != null;
                    assert retrieved.getOcrRequestId().equals(ocrRequestId);
                    assert retrieved.getProductType() == ProductType.AR;
                    assert retrieved.getUnifiedDeliveryDriver().equalsIgnoreCase("POSTE");
                    assert retrieved.getNextRequestIdPcretry().equals("next-request-id-pcretry");
                    assert retrieved.getState() == PaperTrackingsState.DONE;
                    assert retrieved.getEvents() == null;
                    assert retrieved.getValidationFlow() != null;
                    assert retrieved.getValidationFlow().getOcrEnabled().equals(Boolean.TRUE);
                    assert retrieved.getValidationFlow().getDematValidationTimestamp() != null;
                    assert retrieved.getValidationFlow().getSequencesValidationTimestamp() != null;
                    assert retrieved.getPaperStatus() != null;
                    assert retrieved.getPaperStatus().getFinalStatusCode().equals("RECRN005C");
                    assert retrieved.getPaperStatus().getDeliveryFailureCause().equals("M02");
                    assert retrieved.getPaperStatus().getAnonymizedDiscoveredAddress().equals("address discovered");
                    assert retrieved.getPaperStatus().getValidatedEvents() != null;
                    assert retrieved.getPaperStatus().getValidatedEvents().size() == 1;
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getStatusCode().equals("RECRN004C");
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getStatusTimestamp() != null;
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getProductType().equals(ProductType.AR);
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getAttachments() != null;
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getAttachments().size() == 1;
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getAttachments().getFirst().getId().equals("attachment-id-1");
                    assert retrieved.getPaperStatus().getValidatedEvents().getFirst().getAttachments().getFirst().getDocumentType().equals("DOCUMENT_TYPE");
                })
                .blockLast();
    }

    @Test
    void updateItemRequestIdNotExists() {
        //Arrange
        String requestId = "test-request-id";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(requestId);

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
        String ocrRequestId = "test-ocr-request-id";
        paperTrackingsToUpdate.setOcrRequestId(ocrRequestId);

        //Assert
        StepVerifier.create(paperTrackingsDAO.updateItem("non-existing-request-id", paperTrackingsToUpdate))
                .expectError(ConditionalCheckFailedException.class)
                .verify();
    }

    @Test
    void addEvents() {
        //Arrange
        String requestId = "test-request-id-3";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(requestId);
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
        Event event = new Event();
        event.setRequestTimestamp(Instant.now());
        event.setStatusCode("IN_PROGRESS");
        event.setStatusTimestamp(Instant.now());
        event.setProductType(ProductType.AR);
        event.setDryRun(true);

        Attachment attachment = new Attachment();
        attachment.setId("attachment-id-1");
        attachment.setDocumentType("DOCUMENT_TYPE");
        attachment.setUri("http://example.com/document.pdf");
        attachment.setDate(Instant.now());

        Attachment attachment2 = new Attachment();
        attachment2.setId("attachment-id-1");
        attachment2.setDocumentType("DOCUMENT_TYPE");
        attachment2.setUri("http://example.com/document.pdf");

        event.setAttachments(List.of(attachment, attachment2));
        paperTrackingsToUpdate.setEvents(List.of(event));

        paperTrackingsDAO.updateItem(requestId, paperTrackingsToUpdate).block();

        //Assert
        PaperTrackings fisrtResponse = paperTrackingsDAO.retrieveEntityByTrackingId(requestId).block();
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
        event2.setDryRun(false);

        Attachment attachment3 = new Attachment();
        attachment.setId("attachment-id-1");
        attachment.setDocumentType("DOCUMENT_TYPE");
        attachment.setUri("http://example.com/document.pdf");
        attachment.setDate(Instant.now());

        event2.setAttachments(List.of(attachment3));
        paperTrackingsToUpdate1.setEvents(List.of(event2));

        paperTrackingsDAO.updateItem(requestId, paperTrackingsToUpdate1).block();

        PaperTrackings secondeResponse = paperTrackingsDAO.retrieveEntityByTrackingId(requestId).block();
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
