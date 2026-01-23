package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.config.TrackerConfigUtils;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Tracking;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class PaperTrackingsMapper {

    public static Tracking toTracking(PaperTrackings paperTrackings) {
        return SmartMapper.mapToClass(paperTrackings, Tracking.class);
    }

    public static PaperTrackings toPaperTrackings(TrackingCreationRequest trackingCreationRequest, TrackerConfigUtils trackerConfigUtils) {
        ProductType productType = ProductType.fromValue(trackingCreationRequest.getProductType());
        Instant now = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(String.join(".",trackingCreationRequest.getAttemptId(), trackingCreationRequest.getPcRetry()));
        paperTrackings.setUnifiedDeliveryDriver(trackingCreationRequest.getUnifiedDeliveryDriver());
        paperTrackings.setProductType(trackingCreationRequest.getProductType());
        paperTrackings.setState(PaperTrackingsState.AWAITING_REFINEMENT);
        paperTrackings.setBusinessState(BusinessState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setAttemptId(trackingCreationRequest.getAttemptId());
        paperTrackings.setPcRetry(trackingCreationRequest.getPcRetry());
        paperTrackings.setCreatedAt(now);
        paperTrackings.setProcessingMode(trackerConfigUtils.getActualProductsProcessingModes(LocalDate.ofInstant(now, ZoneOffset.UTC)).get(productType));
        PaperStatus paperStatus = new PaperStatus();
        paperTrackings.setPaperStatus(paperStatus);
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setOcrRequests(List.of());
        paperTrackings.setValidationFlow(validationFlow);
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(evaluateIfOcrIsEnabled(trackerConfigUtils, productType));
        validationConfig.setRequiredAttachmentsRefinementStock890(trackerConfigUtils.getActualRequiredAttachmentsRefinementStock890(LocalDate.ofInstant(now, ZoneOffset.UTC)));
        validationConfig.setSendOcrAttachmentsRefinementStock890(trackerConfigUtils.getActualSendOcrAttachmentsRefinementStock890(LocalDate.ofInstant(now, ZoneOffset.UTC)));
        validationConfig.setSendOcrAttachmentsFinalValidation(trackerConfigUtils.getActualSendOcrAttachmentsFinalValidation(LocalDate.ofInstant(now, ZoneOffset.UTC)));
        validationConfig.setSendOcrAttachmentsFinalValidationStock890(trackerConfigUtils.getActualSendOcrAttachmentsFinalValidationStock890(LocalDate.ofInstant(now, ZoneOffset.UTC)));
        validationConfig.setStrictFinalValidationStock890(trackerConfigUtils.getActualStrictFinalValidationStock890(LocalDate.ofInstant(now, ZoneOffset.UTC)));
        paperTrackings.setValidationConfig(validationConfig);
        return paperTrackings;
    }

    private static OcrStatusEnum evaluateIfOcrIsEnabled(TrackerConfigUtils trackerConfigUtils, ProductType productType) {
        return Optional.ofNullable(trackerConfigUtils.getEnableOcrValidationFor().get(productType))
                .orElse(OcrStatusEnum.DISABLED);
    }

}
