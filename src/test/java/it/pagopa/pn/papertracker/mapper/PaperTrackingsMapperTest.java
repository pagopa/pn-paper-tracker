package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.config.TrackerConfigUtils;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Tracking;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class PaperTrackingsMapperTest {

    Duration paperTrackingsTtlDuration = Duration.ofDays(3650);

    @Test
    void toPaperTrackingsValidRequest() {
        //ARRANGE
        TrackingCreationRequest request = new TrackingCreationRequest();
        request.setAttemptId("request123");
        request.setPcRetry("PCRETRY_0");
        request.setUnifiedDeliveryDriver("driver456");
        request.setProductType("RS");

        PnPaperTrackerConfigs pnPaperTrackerConfigs = new PnPaperTrackerConfigs();
        pnPaperTrackerConfigs.setSendOcrAttachmentsFinalValidationStock890(List.of("1970-01-01;ARCAD;CAD"));
        pnPaperTrackerConfigs.setRequiredAttachmentsRefinementStock890(List.of("1970-01-01;23L"));
        pnPaperTrackerConfigs.setSendOcrAttachmentsFinalValidation(List.of("1970-01-01;Plico;AR;23L"));
        pnPaperTrackerConfigs.setStrictFinalValidationStock890(List.of("1970-01-01;true"));
        pnPaperTrackerConfigs.setEnableOcrValidationFor(List.of("AR:RUN","RIR:RUN","890:RUN"));


        TrackerConfigUtils trackerConfigUtils = new TrackerConfigUtils(pnPaperTrackerConfigs);

        //ACT
        PaperTrackings paperTrackings = PaperTrackingsMapper.toPaperTrackings(request,trackerConfigUtils);

        //ASSERT
        Assertions.assertEquals("request123.PCRETRY_0", paperTrackings.getTrackingId());
        Assertions.assertEquals("request123", paperTrackings.getAttemptId());
        Assertions.assertEquals("PCRETRY_0", paperTrackings.getPcRetry());
        Assertions.assertEquals("driver456", paperTrackings.getUnifiedDeliveryDriver());
        Assertions.assertEquals(ProductType.RS.getValue(), paperTrackings.getProductType());
    }

    @Test
    void toPaperTrackingsInvalidProductType() {
        //ARRANGE
        TrackingCreationRequest request = new TrackingCreationRequest();
        request.setAttemptId("request123");
        request.setPcRetry("PCRETRY_0");
        request.setUnifiedDeliveryDriver("driver456");
        request.setProductType("INVALID_TYPE");

        TrackerConfigUtils trackerConfigUtils = new TrackerConfigUtils(new PnPaperTrackerConfigs());

        //ACT & ASSERT
        assertThrows(IllegalArgumentException.class, () -> PaperTrackingsMapper.toPaperTrackings(request, trackerConfigUtils));
    }

    @Test
    void entityToOutputTest(){
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId("tracking123");
        paperTrackings.setAttemptId("attempt123");
        paperTrackings.setPcRetry("PCRETRY_1");
        paperTrackings.setProductType(ProductType._890.getValue());
        paperTrackings.setUnifiedDeliveryDriver("driver789");

        Attachment attachment = new Attachment();
        attachment.setUri("uri");
        attachment.setId("id");
        attachment.setSha256("sha256");
        attachment.setDate(Instant.now());
        attachment.setDocumentType("ARCAD");

        Event event = new Event();
        event.setId("id");
        event.setIun("iun");
        event.setRequestTimestamp(Instant.now());
        event.setStatusCode("RECAG001A");
        event.setStatusDescription("description");
        event.setStatusTimestamp(Instant.now());
        event.setProductType(ProductType._890.getValue());
        event.setDeliveryFailureCause("M06");
        event.setAnonymizedDiscoveredAddressId("ADDR");
        event.setAttachments(List.of(attachment));
        event.setRegisteredLetterCode("regLetterCode123");
        event.setDryRun(true);
        event.setCreatedAt(Instant.now());

        paperTrackings.setEvents(List.of(event));

        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setRegisteredLetterCode("regLetterCode123");
        paperStatus.setDeliveryFailureCause("M01");
        paperStatus.setAnonymizedDiscoveredAddress("ADDR");
        paperStatus.setFinalStatusCode("RECAG003C");
        paperStatus.setValidatedSequenceTimestamp(Instant.now());
        paperStatus.setPaperDeliveryTimestamp(Instant.now());

        paperStatus.setValidatedAttachments(List.of(attachment));
        paperStatus.setValidatedEvents(List.of("event1","event2"));
        paperStatus.setFinalDematFound(true);
        paperTrackings.setPaperStatus(paperStatus);

        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setSequencesValidationTimestamp(Instant.now());
        validationFlow.setFinalEventDematValidationTimestamp(Instant.now());
        validationFlow.setRefinementDematValidationTimestamp(Instant.now());
        validationFlow.setFinalEventBuilderTimestamp(Instant.now());
        validationFlow.setRecag012StatusTimestamp(Instant.now());
        OcrRequest ocrRequest = new OcrRequest();
        ocrRequest.setRequestTimestamp(Instant.now());
        ocrRequest.setResponseTimestamp(Instant.now());
        ocrRequest.setDocumentType("23L");
        ocrRequest.setFinalEventId("eventId");
        validationFlow.setOcrRequests(List.of(ocrRequest));
        paperTrackings.setValidationFlow(validationFlow);

        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(OcrStatusEnum.RUN);
        validationConfig.setRequiredAttachmentsRefinementStock890(List.of("23L"));
        validationConfig.setSendOcrAttachmentsFinalValidationStock890(List.of("ARCAD"));
        validationConfig.setSendOcrAttachmentsFinalValidation(List.of("23L","ARCAD"));
        validationConfig.setStrictFinalValidationStock890(true);
        paperTrackings.setValidationConfig(validationConfig);

        paperTrackings.setNextRequestIdPcretry("nextRequestId123");
        paperTrackings.setState(PaperTrackingsState.DONE);
        paperTrackings.setBusinessState(BusinessState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setCreatedAt(Instant.now());
        paperTrackings.setUpdatedAt(Instant.now());

        Tracking tracking = PaperTrackingsMapper.toTracking(paperTrackings);

        Assertions.assertEquals(paperTrackings.getTrackingId(), tracking.getTrackingId());
        Assertions.assertEquals(paperTrackings.getAttemptId(), tracking.getAttemptId());
        Assertions.assertEquals(paperTrackings.getPcRetry(), tracking.getPcRetry());
        Assertions.assertEquals(paperTrackings.getProductType(), tracking.getProductType());
        Assertions.assertEquals(paperTrackings.getUnifiedDeliveryDriver(), tracking.getUnifiedDeliveryDriver());
        Assertions.assertEquals(paperTrackings.getEvents().size(), tracking.getEvents().size());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getId(), tracking.getEvents().getFirst().getId());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getStatusCode(), tracking.getEvents().getFirst().getStatusCode());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getStatusTimestamp(), tracking.getEvents().getFirst().getStatusTimestamp());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getStatusDescription(), tracking.getEvents().getFirst().getStatusDescription());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getIun(), tracking.getEvents().getFirst().getIun());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getRegisteredLetterCode(), tracking.getEvents().getFirst().getRegisteredLetterCode());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getRequestTimestamp(), tracking.getEvents().getFirst().getRequestTimestamp());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getProductType(), Objects.requireNonNull(tracking.getEvents().getFirst().getProductType()));
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getDeliveryFailureCause(), tracking.getEvents().getFirst().getDeliveryFailureCause());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getAnonymizedDiscoveredAddressId(), tracking.getEvents().getFirst().getAnonymizedDiscoveredAddressId());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getDryRun(), tracking.getEvents().getFirst().getDryRun());
        Assertions.assertNotNull(tracking.getEvents().getFirst().getCreatedAt());
        Assertions.assertEquals(paperTrackings.getEvents().getFirst().getAttachments().size(), Objects.requireNonNull(tracking.getEvents().getFirst().getAttachments()).size());
        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment trackingAttachment = tracking.getEvents().getFirst().getAttachments().getFirst();
        Attachment entityAttachment = paperTrackings.getEvents().getFirst().getAttachments().getFirst();
        Assertions.assertEquals(entityAttachment.getId(), trackingAttachment.getId());
        Assertions.assertEquals(entityAttachment.getUri(), trackingAttachment.getUri());
        Assertions.assertEquals(entityAttachment.getSha256(), trackingAttachment.getSha256());
        Assertions.assertEquals(entityAttachment.getDate(), trackingAttachment.getDate());
        Assertions.assertEquals(entityAttachment.getDocumentType(), trackingAttachment.getDocumentType());


        Assertions.assertNotNull(tracking.getPaperStatus());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getRegisteredLetterCode(), tracking.getPaperStatus().getRegisteredLetterCode());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getDeliveryFailureCause(), tracking.getPaperStatus().getDeliveryFailureCause());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getAnonymizedDiscoveredAddress(), tracking.getPaperStatus().getAnonymizedDiscoveredAddress());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getFinalStatusCode(), tracking.getPaperStatus().getFinalStatusCode());
        Assertions.assertNotNull(tracking.getPaperStatus().getValidatedSequenceTimestamp());
        Assertions.assertNotNull(tracking.getPaperStatus().getPaperDeliveryTimestamp());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getRegisteredLetterCode(), tracking.getPaperStatus().getRegisteredLetterCode());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getRegisteredLetterCode(), tracking.getPaperStatus().getRegisteredLetterCode());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getRegisteredLetterCode(), tracking.getPaperStatus().getRegisteredLetterCode());

        Assertions.assertNotNull(tracking.getPaperStatus().getValidatedAttachments());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getValidatedAttachments().size(), tracking.getPaperStatus().getValidatedAttachments().size());

        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment trackingPaperStatusAttachment = tracking.getPaperStatus().getValidatedAttachments().getFirst();
        Attachment entityPaperStatusAttachment = paperTrackings.getPaperStatus().getValidatedAttachments().get(0);
        Assertions.assertEquals(entityPaperStatusAttachment.getId(), trackingPaperStatusAttachment.getId());
        Assertions.assertEquals(entityPaperStatusAttachment.getUri(), trackingPaperStatusAttachment.getUri());
        Assertions.assertEquals(entityPaperStatusAttachment.getSha256(), trackingPaperStatusAttachment.getSha256());
        Assertions.assertEquals(entityPaperStatusAttachment.getDate(), trackingPaperStatusAttachment.getDate());
        Assertions.assertEquals(entityPaperStatusAttachment.getDocumentType(), trackingPaperStatusAttachment.getDocumentType());
        Assertions.assertNotNull(tracking.getPaperStatus().getValidatedEvents());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getValidatedEvents().size(), tracking.getPaperStatus().getValidatedEvents().size());
        Assertions.assertEquals(paperTrackings.getPaperStatus().getFinalDematFound(), tracking.getPaperStatus().getFinalDematFound());


        Assertions.assertNotNull(tracking.getValidationFlow());
        Assertions.assertNotNull(tracking.getValidationFlow().getSequencesValidationTimestamp());
        Assertions.assertNotNull(tracking.getValidationFlow().getFinalEventDematValidationTimestamp());
        Assertions.assertNotNull(tracking.getValidationFlow().getRefinementDematValidationTimestamp());
        Assertions.assertNotNull(tracking.getValidationFlow().getFinalEventBuilderTimestamp());
        Assertions.assertNotNull(tracking.getValidationFlow().getRecag012StatusTimestamp());
        Assertions.assertNotNull(tracking.getValidationFlow().getOcrRequests());
        Assertions.assertEquals(paperTrackings.getValidationFlow().getOcrRequests().getFirst().getDocumentType(), tracking.getValidationFlow().getOcrRequests().getFirst().getDocumentType());
        Assertions.assertEquals(paperTrackings.getValidationFlow().getOcrRequests().getFirst().getFinalEventId(), tracking.getValidationFlow().getOcrRequests().getFirst().getFinalEventId());
        Assertions.assertEquals(paperTrackings.getValidationFlow().getOcrRequests().getFirst().getAttachmentEventId(), tracking.getValidationFlow().getOcrRequests().getFirst().getAttachmentEventId());

        Assertions.assertNotNull(tracking.getValidationFlow().getOcrRequests().getFirst().getResponseTimestamp());
        Assertions.assertNotNull(tracking.getValidationFlow().getOcrRequests().getFirst().getRequestTimestamp());

        Assertions.assertNotNull(tracking.getValidationConfig());
        Assertions.assertEquals(paperTrackings.getValidationConfig().getOcrEnabled().name(), tracking.getValidationConfig().getOcrEnabled().name());
        Assertions.assertEquals(validationConfig.getSendOcrAttachmentsFinalValidation(), tracking.getValidationConfig().getSendOcrAttachmentsFinalValidation());
        Assertions.assertEquals(validationConfig.getRequiredAttachmentsRefinementStock890(), tracking.getValidationConfig().getRequiredAttachmentsRefinementStock890());
        Assertions.assertEquals(validationConfig.getSendOcrAttachmentsFinalValidationStock890(), tracking.getValidationConfig().getSendOcrAttachmentsFinalValidationStock890());
        Assertions.assertEquals(validationConfig.getStrictFinalValidationStock890(), tracking.getValidationConfig().getStrictFinalValidationStock890());

        Assertions.assertEquals(paperTrackings.getNextRequestIdPcretry(), tracking.getNextRequestIdPcretry());
        Assertions.assertEquals(paperTrackings.getState().name(), tracking.getState().name());
        Assertions.assertEquals(paperTrackings.getBusinessState().name(), tracking.getBusinessState().name());
        Assertions.assertNotNull(tracking.getCreatedAt());
        Assertions.assertNotNull(tracking.getUpdatedAt());
    }

}
