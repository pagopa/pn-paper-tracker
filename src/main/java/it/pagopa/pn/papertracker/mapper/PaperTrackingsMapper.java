package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.config.TrackerConfigUtils;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Tracking;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class PaperTrackingsMapper {

    public static Tracking toTracking(PaperTrackings paperTrackings) {
        return SmartMapper.mapToClass(paperTrackings, Tracking.class);
    }

    public static PaperTrackings toPaperTrackings(TrackingCreationRequest trackingCreationRequest, TrackerConfigUtils trackerConfigUtils) {
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
        paperTrackings.setValidationFlow(new ValidationFlow());
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(evaluateIfOcrIsEnabled(trackerConfigUtils, ProductType.valueOf(trackingCreationRequest.getProductType())));
        validationConfig.setRequiredAttachmentsRefinementStock890(trackerConfigUtils.getActualRequiredAttachmentsRefinementStock890(LocalDate.ofInstant(now, ZoneOffset.UTC)));
        validationConfig.setSendOcrAttachmentsFinalValidation(trackerConfigUtils.getActualSendOcrAttachmentsFinalValidationConfigs(LocalDate.ofInstant(now, ZoneOffset.UTC)));
        validationConfig.setSendOcrAttachmentsFinalValidationStock890(trackerConfigUtils.getActualSendOcrAttachmentsFinalValidationStock890(LocalDate.ofInstant(now, ZoneOffset.UTC)));
        validationConfig.setStrictFinalValidationStock890(trackerConfigUtils.getActualStrictFinalValidationStock890Config(LocalDate.ofInstant(now, ZoneOffset.UTC)));
        paperTrackings.setValidationConfig(validationConfig);
        return paperTrackings;
    }

    private static OcrStatusEnum evaluateIfOcrIsEnabled(TrackerConfigUtils trackerConfigUtils, ProductType productType) {
        return Optional.ofNullable(trackerConfigUtils.getEnableOcrValidationFor().get(productType))
                .orElse(OcrStatusEnum.DISABLED);
    }

    public static PaperTrackings toPaperTrackings(PcRetryResponse pcRetryResponse, ProductType productType, String attemptId) {
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
        return paperTrackings;
    }
}
