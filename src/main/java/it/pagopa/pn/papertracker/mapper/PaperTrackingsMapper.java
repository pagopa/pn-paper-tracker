package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Tracking;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class PaperTrackingsMapper {

    public static Tracking toTracking(PaperTrackings paperTrackings) {
        return SmartMapper.mapToClass(paperTrackings, Tracking.class);
    }

    public static PaperTrackings toPaperTrackings(TrackingCreationRequest trackingCreationRequest, Duration paperTrackingsTtlDuration) {
        Instant now = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(String.join(".",trackingCreationRequest.getAttemptId(), trackingCreationRequest.getPcRetry()));
        paperTrackings.setUnifiedDeliveryDriver(trackingCreationRequest.getUnifiedDeliveryDriver());
        paperTrackings.setProductType(ProductType.valueOf(trackingCreationRequest.getProductType()));
        paperTrackings.setState(PaperTrackingsState.AWAITING_REFINEMENT);
        paperTrackings.setBusinessState(BusinessState.AWAITING_FINAL_STATUS_CODE);
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
        paperTrackings.setState(PaperTrackingsState.AWAITING_REFINEMENT);
        paperTrackings.setBusinessState(BusinessState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setAttemptId(attemptId);
        paperTrackings.setPcRetry(pcRetryResponse.getPcRetry());
        paperTrackings.setCreatedAt(now);
        PaperStatus paperStatus = new PaperStatus();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(paperStatus);
        paperTrackings.setTtl(now.plus(paperTrackingsTtlDuration).getEpochSecond());
        return paperTrackings;
    }
}
