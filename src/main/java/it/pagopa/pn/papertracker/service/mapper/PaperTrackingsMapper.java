package it.pagopa.pn.papertracker.service.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperEvent;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Tracking;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;

import java.time.Duration;
import java.time.Instant;

public class PaperTrackingsMapper {

    private PaperTrackingsMapper() {
        // Utility class, no instantiation
    }

    public static PaperTrackings toPaperTrackings(TrackingCreationRequest trackingCreationRequest, Duration paperTrackingsTtlDuration) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(trackingCreationRequest.getTrackingId());
        paperTrackings.setUnifiedDeliveryDriver(trackingCreationRequest.getUnifiedDeliveryDriver());
        paperTrackings.setProductType(ProductType.valueOf(trackingCreationRequest.getProductType()));
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setTtl(Instant.now().plus(paperTrackingsTtlDuration).toEpochMilli());
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());
        return paperTrackings;
    }

    public static PaperTrackings toPaperTrackings(PcRetryResponse pcRetryResponse, Duration paperTrackingsTtlDuration, ProductType productType) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(pcRetryResponse.getRequestId());
        paperTrackings.setUnifiedDeliveryDriver(pcRetryResponse.getDeliveryDriverId());
        paperTrackings.setProductType(productType);
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setTtl(Instant.now().plus(paperTrackingsTtlDuration).toEpochMilli());
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());
        return paperTrackings;
    }

    public static Tracking toTracking(PaperTrackings paperTrackings) {
        Tracking tracking = new Tracking();
        tracking.setTrackingId(paperTrackings.getTrackingId());
        tracking.setUnifiedDeliveryDriver(paperTrackings.getUnifiedDeliveryDriver());
        tracking.setProductType(
                paperTrackings.getProductType() != null ?
                        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ProductType.valueOf(paperTrackings.getProductType().name()) :
                        null
        );
        tracking.setEvents(
                paperTrackings.getEvents() != null ?
                        paperTrackings.getEvents().stream()
                                .map(PaperTrackingsMapper::toPaperEvent)
                                .collect(java.util.stream.Collectors.toList())
                        : null
        );
        tracking.setValidationFlow(toDtoValidationFlow(paperTrackings.getValidationFlow()));
        tracking.setOcrRequestId(paperTrackings.getOcrRequestId());
        tracking.setNextRequestIdPcretry(paperTrackings.getNextRequestIdPcretry());
        tracking.setPcRetry(paperTrackings.getNextRequestIdPcretry()); // Se serve
        tracking.setPaperStatus(toDtoPaperStatus(paperTrackings.getPaperStatus()));
        tracking.setState(
                paperTrackings.getState() != null ?
                        Tracking.StateEnum.valueOf(paperTrackings.getState().name()) :
                        null
        );
        // attemptId non presente in PaperTrackings al momento
        return tracking;
    }

    private static PaperEvent toPaperEvent(Event event) {
        if (event == null) {
            return null;
        }
        PaperEvent paperEvent = new PaperEvent();
        java.util.Date requestTimestamp = event.getRequestTimestamp() != null ? java.util.Date.from(event.getRequestTimestamp()) : null;
        java.util.Date statusTimestamp = event.getStatusTimestamp() != null ? java.util.Date.from(event.getStatusTimestamp()) : null;
        paperEvent.setRequestTimestamp(requestTimestamp);
        paperEvent.setStatusCode(event.getStatusCode());
        paperEvent.setStatusTimestamp(statusTimestamp);
        paperEvent.setProductType(
                event.getProductType() != null ?
                        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ProductType.valueOf(event.getProductType().name()) :
                        null
        );
        paperEvent.setDeliveryFailureCause(event.getDeliveryFailureCause());
        paperEvent.setRegisteredLetterCode(event.getRegisteredLetterCode());
        paperEvent.setAttachments(
                event.getAttachments() != null ?
                        event.getAttachments().stream()
                                .map(PaperTrackingsMapper::toDtoAttachment)
                                .collect(java.util.stream.Collectors.toList())
                        : null);

        return paperEvent;
    }

    private static it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment toDtoAttachment(
            it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment entityAttachment) {
        if (entityAttachment == null) {
            return null;
        }
        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment dto =
                new it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment();
        dto.setId(entityAttachment.getId());
        dto.setDocumentType(entityAttachment.getDocumentType());
        dto.setUrl(entityAttachment.getUri());
        dto.setDate(entityAttachment.getDate() != null ? entityAttachment.getDate().toString() : null);
        return dto;
    }

    private static it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ValidationFlow toDtoValidationFlow(
            it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ValidationFlow entity) {
        if (entity == null) {
            return null;
        }
        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ValidationFlow dto =
                new it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ValidationFlow();

        dto.setOcrEnabled(entity.getOcrEnabled() != null ? entity.getOcrEnabled().toString() : null);
        dto.setSequencesValidationTimestamp(entity.getSequencesValidationTimestamp() != null ? java.util.Date.from(entity.getSequencesValidationTimestamp()) : null);
        dto.setOcrRequestTimestamp(entity.getOcrRequestTimestamp() != null ? java.util.Date.from(entity.getOcrRequestTimestamp()) : null);
        dto.setDematValidationTimestamp(entity.getDematValidationTimestamp() != null ? java.util.Date.from(entity.getDematValidationTimestamp()) : null);
        return dto;
    }

    private static it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperStatus toDtoPaperStatus(
            it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperStatus entity) {
        if (entity == null) {
            return null;
        }
        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperStatus dto =
                new it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperStatus();

        dto.setRegisteredLetterCode(entity.getRegisteredLetterCode());
        dto.setDeliveryFailureCause(entity.getDeliveryFailureCause());
        dto.setDiscoveredAddress(entity.getDiscoveredAddress());
        dto.setFinalStatusCode(entity.getFinalStatusCode());
        dto.setValidatedSequenceTimestamp(entity.getValidatedSequenceTimestamp() != null ? java.util.Date.from(entity.getValidatedSequenceTimestamp()) : null);
        dto.setValidatedAttachmentUri(entity.getValidatedAttachmentUri());
        dto.setValidatedAttachmentType(entity.getValidatedAttachmentType());
        dto.setValidatedEvents(
                entity.getValidatedEvents() != null ?
                        entity.getValidatedEvents().stream()
                                .map(PaperTrackingsMapper::toPaperEvent)
                                .collect(java.util.stream.Collectors.toList())
                        : null
        );

        return dto;
    }
}
