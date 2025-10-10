package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperEvent;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Tracking;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class PaperTrackingsMapper {

    public static PaperTrackings toPaperTrackings(TrackingCreationRequest trackingCreationRequest, Duration paperTrackingsTtlDuration) {
        Instant now = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(String.join(".",trackingCreationRequest.getAttemptId(), trackingCreationRequest.getPcRetry()));
        paperTrackings.setUnifiedDeliveryDriver(trackingCreationRequest.getUnifiedDeliveryDriver());
        paperTrackings.setProductType(ProductType.valueOf(trackingCreationRequest.getProductType()));
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setAttemptId(trackingCreationRequest.getAttemptId());
        paperTrackings.setPcRetry(trackingCreationRequest.getPcRetry());
        paperTrackings.setCreatedAt(now);
        PaperStatus paperStatus = new PaperStatus();
        paperTrackings.setPaperStatus(paperStatus);
        paperTrackings.setTtl(now.plus(paperTrackingsTtlDuration).getEpochSecond());
        paperTrackings.setValidationFlow(new ValidationFlow());
        return paperTrackings;
    }

    public static PaperTrackings toPaperTrackings(PcRetryResponse pcRetryResponse, Duration paperTrackingsTtlDuration, ProductType productType, String attemptId) {
        Instant now = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(pcRetryResponse.getRequestId());
        paperTrackings.setUnifiedDeliveryDriver(pcRetryResponse.getDeliveryDriverId());
        paperTrackings.setProductType(productType);
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setAttemptId(attemptId);
        paperTrackings.setPcRetry(pcRetryResponse.getPcRetry());
        paperTrackings.setCreatedAt(now);
        PaperStatus paperStatus = new PaperStatus();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(paperStatus);
        paperTrackings.setTtl(now.plus(paperTrackingsTtlDuration).getEpochSecond());
        return paperTrackings;
    }

    public static Tracking toTracking(PaperTrackings paperTrackings) {
        Tracking tracking = new Tracking();
        tracking.setTrackingId(paperTrackings.getTrackingId());
        tracking.setAttemptId(paperTrackings.getAttemptId());
        tracking.setPcRetry(paperTrackings.getPcRetry());
        tracking.setUnifiedDeliveryDriver(paperTrackings.getUnifiedDeliveryDriver());
        tracking.setOcrRequestId(paperTrackings.getOcrRequestId());
        tracking.setNextRequestIdPcretry(paperTrackings.getNextRequestIdPcretry());
        tracking.setCreatedAt(paperTrackings.getCreatedAt());
        tracking.setUpdatedAt(paperTrackings.getUpdatedAt());
        if(Objects.nonNull(paperTrackings.getProductType())){
            tracking.setProductType(it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ProductType.valueOf(paperTrackings.getProductType().getValue()));
        }
        if(!CollectionUtils.isEmpty(paperTrackings.getEvents())){
            tracking.setEvents(paperTrackings.getEvents().stream().map(PaperTrackingsMapper::toPaperEvent).collect(java.util.stream.Collectors.toList()));
        }
        if(Objects.nonNull(paperTrackings.getValidationFlow())) {
            tracking.setValidationFlow(toDtoValidationFlow(paperTrackings.getValidationFlow()));
        }
        if(Objects.nonNull(paperTrackings.getPaperStatus())){
            tracking.setPaperStatus(toDtoPaperStatus(paperTrackings.getPaperStatus()));
        }
        if(Objects.nonNull(paperTrackings.getState())){
            tracking.setState(Tracking.StateEnum.valueOf(paperTrackings.getState().name()));
        }
        return tracking;
    }

    private static PaperEvent toPaperEvent(Event event) {
        PaperEvent paperEvent = new PaperEvent();
        paperEvent.setRequestTimestamp(event.getRequestTimestamp());
        paperEvent.setStatusCode(event.getStatusCode());
        paperEvent.setStatusTimestamp(event.getStatusTimestamp());
        paperEvent.setDeliveryFailureCause(event.getDeliveryFailureCause());
        paperEvent.setRegisteredLetterCode(event.getRegisteredLetterCode());
        paperEvent.setCreatedAt(event.getCreatedAt());
        if(!CollectionUtils.isEmpty(event.getAttachments())){
            paperEvent.setAttachments(event.getAttachments().stream().map(PaperTrackingsMapper::toDtoAttachment).toList());
        }
        if(Objects.nonNull(event.getProductType())) {
            it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ProductType.valueOf(event.getProductType().getValue());
        }
        return paperEvent;
    }

    private static Attachment toDtoAttachment(it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment entityAttachment) {
        Attachment dto = new Attachment();
        dto.setId(entityAttachment.getId());
        dto.setDocumentType(entityAttachment.getDocumentType());
        dto.setUrl(entityAttachment.getUri());
        dto.setDate(entityAttachment.getDate());
        return dto;
    }

    private static it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ValidationFlow toDtoValidationFlow(ValidationFlow entity) {
        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ValidationFlow dto = new it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ValidationFlow();
        dto.setOcrEnabled(entity.getOcrEnabled());
        dto.setSequencesValidationTimestamp(entity.getSequencesValidationTimestamp());
        dto.setOcrRequestTimestamp(entity.getOcrRequestTimestamp());
        dto.setDematValidationTimestamp(entity.getDematValidationTimestamp());
        dto.setFinalEventBuilderTimestamp(entity.getFinalEventBuilderTimestamp());
        return dto;
    }

    private static it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperStatus toDtoPaperStatus(PaperStatus entity) {

        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperStatus dto = new it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperStatus();

        dto.setRegisteredLetterCode(entity.getRegisteredLetterCode());
        dto.setDeliveryFailureCause(entity.getDeliveryFailureCause());
        dto.setDiscoveredAddress(entity.getAnonymizedDiscoveredAddress());
        dto.setFinalStatusCode(entity.getFinalStatusCode());
        dto.setValidatedSequenceTimestamp(entity.getValidatedSequenceTimestamp());
        dto.setValidatedAttachmentUri(entity.getValidatedAttachmentUri());
        dto.setValidatedAttachmentType(entity.getValidatedAttachmentType());
        dto.setFinalDematFound(entity.getFinalDematFound());
        dto.setPaperDeliveryTimestamp(entity.getPaperDeliveryTimestamp());
        dto.setActualPaperDeliveryTimestamp(entity.getActualPaperDeliveryTimestamp());
        if(!CollectionUtils.isEmpty(entity.getValidatedEvents())){
            dto.setValidatedEvents(entity.getValidatedEvents().stream().map(PaperTrackingsMapper::toPaperEvent).toList());
        }
        return dto;
    }
}
