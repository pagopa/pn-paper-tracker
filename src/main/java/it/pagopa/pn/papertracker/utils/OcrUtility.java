package it.pagopa.pn.papertracker.utils;

import com.sngular.apigenerator.asyncapi.business_model.model.event.DataDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.DetailsDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataPayloadDTO;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.FileType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static it.pagopa.pn.papertracker.utils.QueueConst.OCR_REQUEST_EVENT_TYPE;
import static it.pagopa.pn.papertracker.utils.QueueConst.PUBLISHER;

@Component
@RequiredArgsConstructor
@Slf4j
public class OcrUtility {

    private final OcrMomProducer ocrMomProducer;
    private final SafeStorageClient safeStorageClient;
    private final PnPaperTrackerConfigs cfg;
    private final PaperTrackingsDAO paperTrackingsDAO;

    public Mono<Void> checkAndSendToOcr(Event event, List<Attachment> attachmentList, HandlerContext context) {
        PaperTrackings paperTracking = context.getPaperTrackings();
        OcrStatusEnum ocrStatusEnum = context.getPaperTrackings().getValidationConfig().getOcrEnabled();

        if (Objects.nonNull(ocrStatusEnum) && !ocrStatusEnum.equals(OcrStatusEnum.DISABLED)) {
            log.info("OCR validation enabled for trackingId={}", paperTracking.getTrackingId());
            return sendMessageToOcr(event, attachmentList, paperTracking, ocrStatusEnum);
        } else {
            log.info("OCR validation disabled for trackingId={}", paperTracking.getTrackingId());
            return paperTrackingsDAO.updateItem(paperTracking.getTrackingId(), getPaperTrackingsToUpdate(OcrStatusEnum.DISABLED, event, null, attachmentList))
                    .doOnNext(v -> log.debug("Updated PaperTrackings entity with OCR flag disabled for trackingId={}", paperTracking.getTrackingId()))
                    .then();
        }
    }

    private Mono<Void> sendMessageToOcr(Event event, List<Attachment> attachmentList, PaperTrackings paperTracking, OcrStatusEnum ocrStatusEnum) {
        Instant now = Instant.now();
        List<Attachment> validAttachmentList = attachmentList.stream()
                .filter(attachment -> cfg.getEnableOcrValidationForFile().contains(FileType.fromValue(retrieveFileType(attachment.getUri()))))
                .toList();

        if (validAttachmentList.isEmpty()) {
            log.info("No attachment type supported found for OCR, skipping OCR request for trackingId={}", paperTracking.getTrackingId());
            return paperTrackingsDAO.updateItem(paperTracking.getTrackingId(), getPaperTrackingsToUpdate(ocrStatusEnum, event, null, attachmentList))
                    .then();
        }

        List<OcrRequest> ocrRequests = new ArrayList<>();
        return Flux.fromIterable(validAttachmentList)
                .flatMap(attachment -> processAttachmentForOcr(paperTracking, attachment, event, ocrRequests, now))
                .collectList()
                .flatMap(unused -> paperTrackingsDAO.updateItem(paperTracking.getTrackingId(), getPaperTrackingsToUpdate(ocrStatusEnum, event, ocrRequests, null)))
                .doOnNext(unused -> log.info("Demat validation completed for trackingId={}", paperTracking.getTrackingId()))
                .then();
    }

    private Mono<String> processAttachmentForOcr(PaperTrackings paperTracking, Attachment attachment, Event event, List<OcrRequest> ocrRequests, Instant now) {
        String documentType = attachment.getDocumentType();
        String ocrRequestId = TrackerUtility.buildOcrRequestId(paperTracking.getTrackingId(), event.getId(), documentType);
        ocrRequests.add(getOcrRequest(documentType, event.getId(), attachment.getUri(), now));

        return safeStorageClient.getSafeStoragePresignedUrl(attachment.getUri())
                .doOnNext(presignedUrl -> {
                    OcrEvent ocrEvent = buildOcrEvent(paperTracking, ocrRequestId, presignedUrl, DocumentTypeEnum.fromValue(documentType), event);
                    ocrMomProducer.push(ocrEvent);
                });
    }

    private OcrRequest getOcrRequest(String documentType, String eventId, String uri, Instant now) {
        OcrRequest ocrRequest = new OcrRequest();
        ocrRequest.setDocumentType(documentType);
        ocrRequest.setEventId(eventId);
        ocrRequest.setRequestTimestamp(now);
        ocrRequest.setUri(uri);
        return ocrRequest;
    }

    private String retrieveFileType(String uri) {
        return FilenameUtils.getExtension(uri);
    }

    private PaperTrackings getPaperTrackingsToUpdate(OcrStatusEnum ocrStatusEnum, Event event, List<OcrRequest> ocrRequests, List<Attachment> validatedAttachments) {
        PaperTrackings paperTracking = new PaperTrackings();

        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(ocrStatusEnum);


        if (ocrStatusEnum.equals(OcrStatusEnum.DISABLED)) {
            validationConfig.setOcrEnabled(ocrStatusEnum);
            PaperStatus paperStatus = new PaperStatus();
            paperStatus.setValidatedAttachments(validatedAttachments);
            paperTracking.setPaperStatus(paperStatus);
            TrackerUtility.setDematValidationTimestamp(paperTracking, event.getStatusCode());
        } else {
            ValidationFlow validationFlow = new ValidationFlow();
            validationFlow.setOcrRequests(ocrRequests);
            TrackerUtility.setNewStatus(paperTracking, event.getStatusCode(), BusinessState.AWAITING_OCR, PaperTrackingsState.AWAITING_OCR);
            paperTracking.setValidationFlow(validationFlow);
        }
        paperTracking.setValidationConfig(validationConfig);

        return paperTracking;
    }

    private OcrEvent buildOcrEvent(PaperTrackings paperTracking, String ocrRequestId, String presignedUrl, DocumentTypeEnum documentType, Event event) {

        GenericEventHeader ocrHeader = GenericEventHeader.builder()
                .publisher(PUBLISHER)
                .eventId(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .eventType(OCR_REQUEST_EVENT_TYPE)
                .build();

        DetailsDTO.DeliveryFailureCause deliveryFailureCause = StringUtils.isNotBlank(paperTracking.getPaperStatus().getDeliveryFailureCause()) ?
                DetailsDTO.DeliveryFailureCause.valueOf(paperTracking.getPaperStatus().getDeliveryFailureCause()) : null;

        DataDTO.UnifiedDeliveryDriver unifiedDeliveryDriver = StringUtils.isNotBlank(paperTracking.getUnifiedDeliveryDriver()) ?
                DataDTO.UnifiedDeliveryDriver.valueOf(paperTracking.getUnifiedDeliveryDriver().toUpperCase()) : null;

        OcrDataPayloadDTO ocrDataPayload = OcrDataPayloadDTO.builder()
                .version("v1")
                .commandType(OcrDataPayloadDTO.CommandType.POSTAL)
                .commandId(ocrRequestId)
                .data(DataDTO.builder()
                        .documentType(DataDTO.DocumentType.valueOf(documentType.name()))
                        .productType(getProductType(paperTracking))
                        .unifiedDeliveryDriver(unifiedDeliveryDriver)
                        .details(
                                DetailsDTO.builder()
                                        .attachment(presignedUrl)
                                        .registeredLetterCode(paperTracking.getPaperStatus().getRegisteredLetterCode())
                                        .notificationDate(LocalDateTime.ofInstant(event.getStatusTimestamp(), ZoneOffset.UTC))
                                        .deliveryFailureCause(deliveryFailureCause)
                                        .deliveryDetailCode(event.getStatusCode())
                                        .build()
                        )
                        .build())
                .build();

        return new OcrEvent(ocrHeader, ocrDataPayload);
    }

    private DataDTO.ProductType getProductType(PaperTrackings paperTracking) {
        return Arrays.stream(DataDTO.ProductType.values()).filter(ocrProductType -> ocrProductType.getValue().equals(paperTracking.getProductType().getValue()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("invalid ProductType for trackingId={}: {}", paperTracking.getTrackingId(), paperTracking.getProductType());
                    return new IllegalArgumentException("Invalid product type: " + paperTracking.getProductType());
                });
    }
}
